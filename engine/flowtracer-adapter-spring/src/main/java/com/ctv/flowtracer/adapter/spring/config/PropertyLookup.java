package com.ctv.flowtracer.adapter.spring.config;

import com.ctv.flowtracer.ir.spi.SourceContentLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Giải thuộc tính cấu hình Spring trong chuỗi {@code ${khóa}} / {@code ${khóa:mặc định}} (vd đường dẫn của
 * {@code @GetMapping}). Tra theo yêu cầu: chỉ các khóa mã nguồn thực sự tham chiếu, trong file {@code application.yml /
 * .yaml / .properties} MẶC ĐỊNH của module chứa file mã nguồn. Không lưu giá trị nào.
 *
 * <p>Thứ tự ưu tiên giống Spring: giá trị trong file cấu hình, rồi mặc định viết trong code, rồi "chưa giải được". Không
 * đoán: khóa nằm ở biến môi trường, chỉ có trong file profile, hoặc bị chặn thì kết quả là chưa giải được kèm lý do.
 *
 * <p>An toàn:
 *
 * <ul>
 *   <li>khóa có đoạn {@code password / secret / token / credential} bị chặn, trừ khi đoạn cuối của khóa là
 *       {@code path / paths / endpoint / uri / route} (vd {@code jwt.token-path}) VÀ giá trị bắt đầu bằng {@code /};
 *   <li>giá trị đọc từ file cấu hình phải có hình dạng đường dẫn (không khoảng trắng, tối đa {@value #MAX_VALUE_LENGTH} ký
 *       tự), nếu không bị bỏ.
 * </ul>
 */
public final class PropertyLookup {

    /** Lý do một thuộc tính chưa giải được. */
    public enum Reason {
        /** Không có trong file cấu hình mặc định và code không có giá trị mặc định. */
        ABSENT,
        /** Giá trị trỏ tới biến môi trường lúc chạy (không có mặc định) — tool không biết. */
        ENVIRONMENT,
        /** Chỉ có trong file theo profile (vd {@code application-dev.yml}) — không biết profile nào chạy. */
        PROFILE_ONLY,
        /** Tên khóa thuộc nhóm nhạy cảm — không đọc. */
        BLOCKED,
        /** Giá trị trong file cấu hình không có hình dạng đường dẫn. */
        BAD_VALUE,
        /** Khóa tham chiếu vòng hoặc lồng quá sâu. */
        CYCLE
    }

    /**
     * Kết quả giải một chuỗi.
     *
     * @param text chuỗi đã thay các thuộc tính giải được; thuộc tính chưa giải được giữ nguyên dạng {@code ${khóa}}
     * @param failure lý do chưa giải được (null nếu giải trọn vẹn); là lý do của thuộc tính đầu tiên thất bại
     * @param failedKey khóa thất bại (null nếu giải trọn vẹn)
     * @param sources căn cứ từng thuộc tính đã giải, vd {@code shop.path = /x (src/main/resources/application.yml)}
     */
    public record Resolution(String text, Reason failure, String failedKey, List<String> sources) {

        public boolean resolved() {
            return failure == null;
        }
    }

    public static final int MAX_VALUE_LENGTH = 200;

    static final String FROM_CODE_DEFAULT = "mặc định trong code";

    private static final int MAX_DEPTH = 5;
    private static final Set<String> SENSITIVE_SEGMENTS =
            Set.of("password", "passwd", "secret", "token", "credential", "credentials");
    /** Đoạn cuối cho biết khóa là một ĐƯỜNG DẪN (vd {@code jwt.token-path}) chứ không phải chính giá trị nhạy cảm. */
    private static final Set<String> PATH_SUFFIXES = Set.of("path", "paths", "endpoint", "uri", "route");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");
    private static final Pattern PATH_SHAPE = Pattern.compile("^[\\w\\-./{}~%]*$");
    private static final Pattern DEFAULT_CONFIG = Pattern.compile("^(?:(.*)/)?src/main/resources/application\\.(yml|yaml|properties)$");
    private static final Pattern PROFILE_CONFIG =
            Pattern.compile("^(?:(.*)/)?src/main/resources/application-[^/]+\\.(yml|yaml|properties)$");

    private final List<ConfigFile> defaults;
    private final List<ConfigFile> profiles;
    private final SourceContentLoader loader;

    /** Một file cấu hình và thư mục module của nó. */
    private record ConfigFile(String moduleRoot, String path) {}

    public PropertyLookup(List<String> allPaths, SourceContentLoader loader) {
        this.loader = loader;
        this.defaults = filesMatching(allPaths, DEFAULT_CONFIG);
        this.profiles = filesMatching(allPaths, PROFILE_CONFIG);
    }

    /**
     * @param text chuỗi có thể chứa {@code ${...}}
     * @param sourceFile file mã nguồn chứa chuỗi (tương đối trong Source) — để chọn module
     */
    public Resolution resolve(String text, String sourceFile) {
        var sources = new ArrayList<String>();
        var failure = new Failure[1];
        String result = expand(text, sourceFile, 0, sources, failure);
        return new Resolution(
                result, failure[0] == null ? null : failure[0].reason, failure[0] == null ? null : failure[0].key, List.copyOf(sources));
    }

    /** Giải thích bằng tiếng Việt cho người dùng. */
    public static String describe(Reason reason, String key) {
        return switch (reason) {
            case ABSENT -> "khóa " + key + " không có trong file cấu hình mặc định và code không có giá trị mặc định";
            case ENVIRONMENT -> "khóa " + key + " lấy từ biến môi trường lúc chạy, tool không biết giá trị";
            case PROFILE_ONLY -> "khóa " + key + " chỉ có trong file theo profile, tool không biết profile nào được dùng";
            case BLOCKED -> "khóa " + key + " thuộc nhóm nhạy cảm nên tool không đọc";
            case BAD_VALUE -> "giá trị của khóa " + key + " không có dạng đường dẫn nên không dùng";
            case CYCLE -> "khóa " + key + " tham chiếu vòng hoặc lồng quá sâu";
        };
    }

    private record Failure(Reason reason, String key) {}

    private String expand(String text, String sourceFile, int depth, List<String> sources, Failure[] failure) {
        Matcher m = PLACEHOLDER.matcher(text);
        var out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1).strip();
            String codeDefault = m.group(2);
            Optional<String> value = valueOf(key, codeDefault, sourceFile, depth, sources, failure);
            m.appendReplacement(out, Matcher.quoteReplacement(value.orElse(m.group())));
        }
        m.appendTail(out);
        return out.toString();
    }

    private Optional<String> valueOf(
            String key, String codeDefault, String sourceFile, int depth, List<String> sources, Failure[] failure) {
        boolean sensitive = isBlocked(key);
        if (sensitive && !isPathKey(key)) {
            return fail(failure, Reason.BLOCKED, key);
        }
        if (depth >= MAX_DEPTH) {
            return fail(failure, Reason.CYCLE, key);
        }
        Optional<ConfigFile> file = moduleFiles(defaults, sourceFile).stream()
                .filter(f -> read(f, key).isPresent())
                .findFirst();
        Optional<String> raw = file.flatMap(f -> read(f, key));
        if (raw.isPresent()) {
            // Giá trị trong file cấu hình có thể lại chứa ${...} (biến môi trường, khóa khác)
            var inner = new Failure[1];
            String expanded = expand(raw.get().strip(), file.get().path(), depth + 1, new ArrayList<>(), inner);
            if (inner[0] != null) {
                Reason reason = inner[0].reason == Reason.ABSENT ? Reason.ENVIRONMENT : inner[0].reason;
                return fail(failure, reason, inner[0].reason == Reason.ABSENT ? inner[0].key : key);
            }
            if (sensitive && !expanded.startsWith("/")) {
                return fail(failure, Reason.BLOCKED, key);
            }
            if (expanded.length() > MAX_VALUE_LENGTH || !PATH_SHAPE.matcher(expanded).matches()) {
                return fail(failure, Reason.BAD_VALUE, key);
            }
            sources.add(key + " = " + expanded + " (" + file.get().path() + ")");
            return Optional.of(expanded);
        }
        if (codeDefault != null) {
            sources.add(key + " = " + codeDefault + " (" + FROM_CODE_DEFAULT + ")");
            return Optional.of(codeDefault);
        }
        boolean inProfile = moduleFiles(profiles, sourceFile).stream().anyMatch(f -> read(f, key).isPresent());
        return fail(failure, inProfile ? Reason.PROFILE_ONLY : Reason.ABSENT, key);
    }

    private static Optional<String> fail(Failure[] failure, Reason reason, String key) {
        if (failure[0] == null) {
            failure[0] = new Failure(reason, key);
        }
        return Optional.empty();
    }

    /** Tên khóa có đoạn nhạy cảm (tách theo mọi ký tự không phải chữ/số và theo chữ hoa trong camelCase). */
    static boolean isBlocked(String key) {
        return segments(key).stream().anyMatch(SENSITIVE_SEGMENTS::contains);
    }

    /** Đoạn cuối của khóa cho biết đây là khóa đường dẫn. */
    static boolean isPathKey(String key) {
        List<String> parts = segments(key);
        return !parts.isEmpty() && PATH_SUFFIXES.contains(parts.getLast());
    }

    private static List<String> segments(String key) {
        String spaced = key.replaceAll("([a-z0-9])([A-Z])", "$1.$2").toLowerCase(Locale.ROOT);
        return List.of(spaced.split("[^a-z0-9]+"));
    }

    private Optional<String> read(ConfigFile file, String key) {
        Optional<String> content = loader.load(file.path());
        if (content.isEmpty()) {
            return Optional.empty();
        }
        String value = file.path().endsWith(".properties")
                ? ApplicationConfigReader.propertiesValue(content.get(), key)
                : ApplicationConfigReader.yamlValue(content.get(), key);
        return Optional.ofNullable(value);
    }

    private static List<ConfigFile> filesMatching(List<String> allPaths, Pattern pattern) {
        var result = new ArrayList<ConfigFile>();
        for (String path : allPaths.stream().sorted().toList()) {
            Matcher m = pattern.matcher(path);
            if (m.matches()) {
                result.add(new ConfigFile(m.group(1) == null ? "" : m.group(1), path));
            }
        }
        return result;
    }

    /** Các file của module sâu nhất chứa file mã nguồn; .properties đứng trước .yml như thứ tự ưu tiên của Spring. */
    private static List<ConfigFile> moduleFiles(List<ConfigFile> files, String sourceFile) {
        List<ConfigFile> inside = files.stream()
                .filter(f -> f.moduleRoot().isEmpty() || sourceFile.startsWith(f.moduleRoot() + "/"))
                .toList();
        int deepest = inside.stream().mapToInt(f -> f.moduleRoot().length()).max().orElse(-1);
        return inside.stream()
                .filter(f -> f.moduleRoot().length() == deepest)
                .sorted(Comparator.comparing((ConfigFile f) -> !f.path().endsWith(".properties")).thenComparing(ConfigFile::path))
                .toList();
    }
}
