package com.ctv.flowtracer.core.detection;

import com.ctv.flowtracer.core.detection.FolderDetection.LegacyHint;
import com.ctv.flowtracer.core.detection.FolderDetection.ProjectRoot;
import com.ctv.flowtracer.core.detection.FolderDetection.Status;
import com.ctv.flowtracer.core.discovery.DefaultExcludes;
import com.ctv.flowtracer.ir.SourceRole;
import com.ctv.flowtracer.ir.spi.DetectContext;
import com.ctv.flowtracer.ir.spi.DetectResult;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Nhận diện thư mục người dùng chọn: tìm các gốc dự án bên trong (tối đa {@link #MAX_DEPTH} tầng), dò ngược lên thư
 * mục cha khi thư mục nằm bên trong một dự án, và gợi ý thư mục chứa mã của công nghệ chưa hỗ trợ.
 *
 * <p>Không chứa logic riêng của công nghệ nào: mỗi adapter tự khai báo file đặc trưng ({@link StackAdapter#rootMarkers()})
 * và tự chấm điểm ({@link StackAdapter#detect(DetectContext)}). Kết quả sắp xếp ổn định theo đường dẫn.
 */
public final class FolderDetector {

    /** Số tầng thư mục con tối đa được duyệt khi tìm gốc dự án. */
    public static final int MAX_DEPTH = 4;
    /** Số tầng tối đa khi đếm file cho gợi ý mã công nghệ chưa hỗ trợ (mã cũ thường nằm sâu, vd {@code docs/...}). */
    public static final int HINT_DEPTH = 8;
    /** Số thư mục tối đa được duyệt trong một lần nhận diện. */
    public static final int MAX_DIRS = 5000;

    static final int HINT_MIN_FILES = 10;
    static final double HINT_MIN_RATIO = 0.5;

    private static final Comparator<ProjectRoot> BY_PATH =
            Comparator.comparing(ProjectRoot::path).thenComparing(ProjectRoot::adapterId);

    /**
     * @param selectedDir thư mục người dùng chọn, tương đối so với {@code SOURCE_ROOT}
     * @param adapters các adapter đang có
     * @param hintLanguages ngôn ngữ chưa hỗ trợ → các đuôi file (chữ thường, không dấu chấm), dùng cho gợi ý. Đuôi đầu
     *     tiên là đuôi chính, phải có ít nhất {@link #HINT_MIN_FILES} file; các đuôi sau (vd template) chỉ cộng vào tỉ lệ
     */
    public FolderDetection detect(
            String selectedDir, List<StackAdapter> adapters, FolderAccess access, Map<String, List<String>> hintLanguages) {
        String selected = normalize(selectedDir);
        var failed = new TreeSet<String>();
        Walk walk = walk(selected, access);

        var found = new ArrayList<ProjectRoot>();
        walk.files().forEach((dir, files) -> {
            if (walk.depths().get(dir) <= MAX_DEPTH) {
                found.addAll(detectAt(dir, files, adapters, access, failed));
            }
        });
        List<ProjectRoot> roots = keepOutermostPerAdapter(found);

        // Thư mục cha, gần nhất trước
        var ancestorRoots = new ArrayList<ProjectRoot>();
        for (String dir = parentOf(selected); dir != null; dir = parentOf(dir)) {
            ancestorRoots.addAll(detectAt(dir, access.fileNames().apply(dir), adapters, access, failed));
        }

        List<LegacyHint> hints = legacyHints(walk, roots, hintLanguages);
        Optional<ProjectRoot> suggestion = suggestion(roots, ancestorRoots);
        Status status;
        if (suggestion.isPresent()) {
            status = Status.INSIDE_PROJECT;
        } else {
            status = roots.isEmpty() ? Status.NOT_FOUND : Status.VALID;
        }
        return new FolderDetection(
                status,
                status == Status.VALID ? roots : List.of(),
                suggestion.orElse(null),
                hints,
                List.copyOf(failed),
                walk.truncated());
    }

    /** Chạy detect của các adapter có file đặc trưng nằm ngay trong {@code dir}. */
    private static List<ProjectRoot> detectAt(
            String dir, List<String> files, List<StackAdapter> adapters, FolderAccess access, TreeSet<String> failed) {
        var result = new ArrayList<ProjectRoot>();
        List<String> sortedFiles = files.stream().sorted().toList();
        for (StackAdapter adapter : adapters) {
            if (adapter.rootMarkers().stream().noneMatch(sortedFiles::contains)) {
                continue;
            }
            DetectResult r;
            try {
                r = adapter.detect(new DetectContext(sortedFiles, rel -> access.content().load(join(dir, rel))));
            } catch (RuntimeException e) {
                failed.add(adapter.id());
                continue;
            }
            if (r.score() >= StackDetector.MIN_SCORE) {
                SourceRole role = adapter.roles().stream().sorted().findFirst().orElseThrow();
                result.add(new ProjectRoot(dir, adapter.id(), role, r.stackName(), r.version(), r.score(), r.reason()));
            }
        }
        return result;
    }

    /** Cùng một adapter thì chỉ giữ gốc ở cấp cao nhất (vd dự án Maven nhiều module lấy pom.xml gốc). */
    static List<ProjectRoot> keepOutermostPerAdapter(List<ProjectRoot> found) {
        return found.stream()
                .filter(r -> found.stream()
                        .noneMatch(o -> o.adapterId().equals(r.adapterId()) && isStrictlyInside(r.path(), o.path())))
                .sorted(BY_PATH)
                .toList();
    }

    /**
     * Thư mục nằm bên trong một dự án khi: không có gốc nào bên trong mà thư mục cha là gốc dự án, hoặc gốc tìm thấy chỉ
     * là module con của một gốc cùng công nghệ ở cấp trên. Gợi ý gốc cùng công nghệ ở cấp cao nhất.
     */
    private static Optional<ProjectRoot> suggestion(List<ProjectRoot> roots, List<ProjectRoot> ancestorRoots) {
        if (ancestorRoots.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> adapterId = roots.isEmpty()
                ? Optional.of(ancestorRoots.getFirst().adapterId())
                : roots.stream()
                        .map(ProjectRoot::adapterId)
                        .filter(id -> ancestorRoots.stream().anyMatch(a -> a.adapterId().equals(id)))
                        .findFirst();
        return adapterId.flatMap(id -> ancestorRoots.stream()
                .filter(a -> a.adapterId().equals(id))
                .reduce((nearer, higher) -> higher));
    }

    /** Thư mục cao nhất có nhiều file của ngôn ngữ chưa hỗ trợ, không phải và không chứa gốc dự án. */
    private static List<LegacyHint> legacyHints(
            Walk walk, List<ProjectRoot> roots, Map<String, List<String>> hintLanguages) {
        var hints = new ArrayList<LegacyHint>();
        for (var lang : new TreeMap<>(hintLanguages).entrySet()) {
            String language = lang.getKey();
            List<String> exts = lang.getValue();
            // thư mục → {số file của ngôn ngữ, tổng số file, số file đuôi chính} trong cây con
            Map<String, int[]> counts = new TreeMap<>();
            walk.files().forEach((dir, files) -> {
                int matched = (int) files.stream().filter(f -> exts.contains(extensionOf(f))).count();
                int primary = (int) files.stream().filter(f -> extensionOf(f).equals(exts.getFirst())).count();
                for (String d = dir; d != null; d = d.equals(walk.selected()) ? null : parentOf(d)) {
                    int[] c = counts.computeIfAbsent(d, k -> new int[3]);
                    c[0] += matched;
                    c[1] += files.size();
                    c[2] += primary;
                }
            });
            var chosen = new ArrayList<String>();
            counts.forEach((dir, c) -> {
                boolean dense = c[2] >= HINT_MIN_FILES && c[0] >= HINT_MIN_RATIO * c[1];
                boolean underChosen = chosen.stream().anyMatch(p -> isStrictlyInside(dir, p));
                boolean touchesRoot =
                        roots.stream().anyMatch(r -> r.path().equals(dir) || isStrictlyInside(r.path(), dir));
                if (dense && !underChosen && !touchesRoot) {
                    chosen.add(dir);
                    String target = narrow(dir, c[0], counts, walk);
                    hints.add(new LegacyHint(target, language, c[0]));
                }
            });
        }
        hints.sort(Comparator.comparing(LegacyHint::path).thenComparing(LegacyHint::language));
        return hints;
    }

    /** Đi xuống khi toàn bộ file khớp nằm trong đúng một thư mục con (vd {@code docs} → {@code docs/old-shop}). */
    private static String narrow(String dir, int matched, Map<String, int[]> counts, Walk walk) {
        String current = dir;
        while (true) {
            String prefix = current + "/";
            List<String> holders = counts.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(prefix) && e.getKey().indexOf('/', prefix.length()) < 0)
                    .filter(e -> e.getValue()[0] > 0)
                    .map(Map.Entry::getKey)
                    .toList();
            if (holders.size() != 1 || counts.get(holders.getFirst())[0] != matched || !walk.files().containsKey(current)) {
                return current;
            }
            current = holders.getFirst();
        }
    }

    /**
     * Duyệt theo chiều rộng tới {@link #HINT_DEPTH}, sắp xếp ổn định, bỏ qua thư mục build/thư viện/ẩn. Vì duyệt theo chiều
     * rộng nên mọi thư mục trong {@link #MAX_DEPTH} được thăm trước; {@code truncated} chỉ bật khi chưa thăm hết các thư
     * mục đó.
     */
    private static Walk walk(String selected, FolderAccess access) {
        var files = new LinkedHashMap<String, List<String>>();
        var depths = new LinkedHashMap<String, Integer>();
        Deque<Map.Entry<String, Integer>> queue = new ArrayDeque<>();
        queue.add(Map.entry(selected, 0));
        boolean truncated = false;
        while (!queue.isEmpty()) {
            var item = queue.poll();
            String dir = item.getKey();
            int depth = item.getValue();
            if (files.size() >= MAX_DIRS) {
                truncated = depth <= MAX_DEPTH;
                break;
            }
            files.put(dir, access.fileNames().apply(dir).stream().sorted().toList());
            depths.put(dir, depth);
            if (depth < HINT_DEPTH) {
                access.subdirNames().apply(dir).stream()
                        .filter(n -> !DefaultExcludes.isSkippedDir(n))
                        .sorted()
                        .forEach(n -> queue.add(Map.entry(join(dir, n), depth + 1)));
            }
        }
        return new Walk(selected, files, depths, truncated);
    }

    private record Walk(
            String selected, Map<String, List<String>> files, Map<String, Integer> depths, boolean truncated) {}

    /** Chuẩn hóa đường dẫn tương đối: dấu '/', bỏ '/' ở hai đầu, {@code "."} thành {@code ""}. */
    public static String normalize(String path) {
        String p = path == null ? "" : path.replace('\\', '/').strip();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.equals(".") ? "" : p;
    }

    static String join(String dir, String name) {
        return dir.isEmpty() ? name : dir + "/" + name;
    }

    /** Thư mục cha; {@code null} nếu đã là {@code SOURCE_ROOT}. */
    static String parentOf(String dir) {
        if (dir.isEmpty()) {
            return null;
        }
        int slash = dir.lastIndexOf('/');
        return slash < 0 ? "" : dir.substring(0, slash);
    }

    /** {@code child} nằm hẳn bên trong {@code parent} (không tính trường hợp trùng nhau). */
    public static boolean isStrictlyInside(String child, String parent) {
        return parent.isEmpty() ? !child.isEmpty() : child.startsWith(parent + "/");
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
