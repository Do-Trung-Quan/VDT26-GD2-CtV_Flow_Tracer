package com.ctv.flowtracer.adapter.spring.library;

import com.ctv.flowtracer.adapter.spring.library.ClassFileReader.Annotation;
import com.ctv.flowtracer.adapter.spring.library.ClassFileReader.ClassInfo;
import com.ctv.flowtracer.adapter.spring.library.ClassFileReader.Field;
import com.ctv.flowtracer.ir.spi.LibraryArchive;
import com.ctv.flowtracer.ir.spi.SourceFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Dựng "mã nguồn rút gọn" (chỉ khai báo kiểu và trường) cho những lớp nằm trong jar của dự án mà mã dự án thật sự dùng — vd lớp
 * bọc {@code ApiResponse<T>} của thư viện lõi. Các file rút gọn đi qua bộ phân tích như mã nguồn thường nên kiểu trả về của API
 * được mở đúng như tài liệu Swagger.
 *
 * <p>Chỉ ĐỌC khai báo trong file {@code .class} (xem {@link ClassFileReader}); không nạp lớp, không chạy mã. Chỉ lấy lớp mà mã dự
 * án import (hoặc dùng cùng gói) và các lớp chúng tham chiếu tới, tối đa {@value #MAX_STUBS} lớp; lớp đã có mã nguồn thật thì bỏ
 * qua. Cùng jar, cùng mã dự án ⇒ cùng kết quả.
 */
public final class LibraryStubBuilder {

    static final int MAX_STUBS = 300;
    private static final int MAX_ENTRY_BYTES = 512 * 1024;
    private static final int MAX_CLASSES_PER_JAR = 60_000;
    private static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;");
    private static final Pattern CLASS_TOKEN = Pattern.compile("\\b[A-Z][A-Za-z0-9_]*\\b");

    /** Annotation giữ lại trên trường: tên JSON, bỏ qua, ràng buộc kiểm tra dữ liệu, mô tả Swagger. */
    private static final Set<String> KEPT_ANNOTATIONS = Set.of(
            "JsonProperty", "JsonAlias", "JsonIgnore", "NotNull", "NotBlank", "NotEmpty", "Size", "Min", "Max", "DecimalMin",
            "DecimalMax", "Email", "Pattern", "Positive", "PositiveOrZero", "Negative", "NegativeOrZero", "Past", "PastOrPresent",
            "Future", "FutureOrPresent", "Schema");
    private static final Set<String> SKIPPED_ELEMENTS = Set.of("groups", "payload");

    /** Một lớp trong jar: bytes của file {@code .class} và jar chứa nó. */
    private record Entry(String archivePath, byte[] bytes) {}

    public List<SourceFile> build(List<LibraryArchive> archives, List<SourceFile> projectFiles) {
        if (projectFiles.isEmpty()) {
            return List.of();
        }
        Usage usage = Usage.of(projectFiles);
        var stubs = new TreeMap<String, SourceFile>();
        addFrameworkStubs(usage, stubs);
        Map<String, Entry> classes = archives.isEmpty() ? Map.of() : openArchives(archives);
        if (classes.isEmpty()) {
            return List.copyOf(stubs.values());
        }
        Deque<String> queue = new ArrayDeque<>();
        Set<String> wanted = new LinkedHashSet<>();
        for (String fqn : classes.keySet()) { // TreeMap: thứ tự ổn định
            if (!usage.defined.contains(fqn) && usage.uses(fqn) && wanted.add(fqn)) {
                queue.add(fqn);
            }
        }
        while (!queue.isEmpty() && stubs.size() < MAX_STUBS) {
            String fqn = queue.poll();
            Entry entry = classes.get(fqn);
            try {
                Rendered rendered = render(ClassFileReader.read(entry.bytes()));
                if (rendered == null) {
                    continue;
                }
                String path = entry.archivePath() + "!/" + fqn.replace('.', '/') + ".java";
                stubs.put(path, new SourceFile(path, rendered.text(), sha256(rendered.text())));
                for (String next : rendered.referenced()) {
                    if (classes.containsKey(next) && !usage.defined.contains(next) && wanted.add(next)) {
                        queue.add(next);
                    }
                }
            } catch (IOException | RuntimeException e) {
                // lớp lạ / hỏng: bỏ qua riêng lớp đó, các lớp khác vẫn dùng được
            }
        }
        return List.copyOf(stubs.values());
    }

    // ---------------- kiểu của framework ----------------

    /**
     * Kiểu của framework mà mã dự án hay trả về nhưng không có mã nguồn trong repo: dựng khai báo theo đúng dạng JSON mà
     * Spring / springdoc xuất ra, để kết quả phân trang cũng được mở như mọi kiểu khác.
     */
    private static final Map<String, String> FRAMEWORK = Map.of("org.springframework.data.domain.Page", """
            package org.springframework.data.domain;
            public class Page<T> {
                private java.util.List<T> content;
                private long totalElements;
                private int totalPages;
                private int size;
                private int number;
                private int numberOfElements;
                private boolean first;
                private boolean last;
                private boolean empty;
            }
            """);

    private static void addFrameworkStubs(Usage usage, Map<String, SourceFile> stubs) {
        FRAMEWORK.forEach((fqn, text) -> {
            if (!usage.defined.contains(fqn) && usage.uses(fqn)) {
                String path = "(framework)!/" + fqn.replace('.', '/') + ".java";
                stubs.put(path, new SourceFile(path, text, sha256(text)));
            }
        });
    }

    // ---------------- đọc jar ----------------

    /** Các lớp cấp cao nhất của mọi jar (lớp lồng và {@code module-info} bỏ qua), theo tên đầy đủ; jar đứng trước thắng. */
    private static Map<String, Entry> openArchives(List<LibraryArchive> archives) {
        Map<String, Entry> classes = new TreeMap<>();
        long total = 0;
        for (LibraryArchive archive : archives.stream().sorted(Comparator.comparing(LibraryArchive::relativePath)).toList()) {
            int count = 0;
            try (var zip = new ZipInputStream(new ByteArrayInputStream(archive.content()))) {
                for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                    String name = e.getName();
                    if (e.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/") || name.contains("$")
                            || name.endsWith("module-info.class") || name.endsWith("package-info.class")) {
                        continue;
                    }
                    if (++count > MAX_CLASSES_PER_JAR || total > MAX_TOTAL_BYTES) {
                        break;
                    }
                    byte[] bytes = readLimited(zip);
                    if (bytes == null) {
                        continue;
                    }
                    total += bytes.length;
                    classes.putIfAbsent(name.substring(0, name.length() - ".class".length()).replace('/', '.'),
                            new Entry(archive.relativePath(), bytes));
                }
            } catch (IOException | RuntimeException e) {
                // jar hỏng: bỏ qua jar này
            }
        }
        return classes;
    }

    private static byte[] readLimited(ZipInputStream zip) throws IOException {
        var out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = zip.read(buffer)) > 0) {
            if (out.size() + n > MAX_ENTRY_BYTES) {
                return null; // lớp quá lớn: không phải DTO
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    // ---------------- dựng file rút gọn ----------------

    private record Rendered(String text, Set<String> referenced) {}

    private static Rendered render(ClassInfo info) {
        if ((info.access() & ClassFileReader.ACC_ANNOTATION) != 0 || (info.access() & ClassFileReader.ACC_INTERFACE) != 0 || !dataLike(info)) {
            // Interface và lớp có hành vi (repository, service, tiện ích…) KHÔNG dựng mã rút gọn: không có hàm thì bộ phân tích
            // sẽ coi lời gọi tới chúng là "hàm không tồn tại" thay vì nhận ra đó là thư viện ngoài (vd thực thi truy vấn)
            return null;
        }
        String fqn = info.internalName().replace('/', '.');
        int dot = fqn.lastIndexOf('.');
        String pkg = dot < 0 ? "" : fqn.substring(0, dot);
        String simple = fqn.substring(dot + 1);
        var types = new StubTypes();
        boolean isEnum = (info.access() & ClassFileReader.ACC_ENUM) != 0;
        boolean isInterface = (info.access() & ClassFileReader.ACC_INTERFACE) != 0;

        String typeParams = "";
        String extendsClause = "";
        if (info.signature() != null) {
            var sig = types.classSignature(info.signature());
            if (!sig.typeParameters().isEmpty()) {
                typeParams = "<" + String.join(", ", sig.typeParameters()) + ">";
            }
            if (!isInterface && !isEnum && sig.superType() != null && !isTrivialSuper(sig.superType())) {
                extendsClause = " extends " + sig.superType();
            }
        } else if (!isInterface && !isEnum && info.superName() != null && !isTrivialSuper(info.superName().replace('/', '.'))) {
            extendsClause = " extends " + types.ref(info.superName());
        }

        var body = new StringBuilder();
        if (isEnum) {
            List<String> constants = info.fields().stream().filter(f -> (f.access() & ClassFileReader.ACC_ENUM) != 0).map(Field::name).toList();
            body.append("    ").append(String.join(", ", constants)).append(";\n");
        }
        if (!isInterface) {
            for (Field f : info.fields()) {
                String line = field(f, types, isEnum);
                if (line != null) {
                    body.append(line);
                }
            }
        }
        String kind = isInterface ? "interface" : isEnum ? "enum" : (info.access() & ClassFileReader.ACC_ABSTRACT) != 0 ? "abstract class" : "class";

        var out = new StringBuilder();
        out.append("// Mã rút gọn do tool dựng từ khai báo trong thư viện (chỉ có kiểu và trường), không phải mã nguồn thật.\n");
        if (!pkg.isEmpty()) {
            out.append("package ").append(pkg).append(";\n\n");
        }
        for (String imported : types.imports()) {
            if (!imported.equals(fqn)) {
                out.append("import ").append(imported).append(";\n");
            }
        }
        out.append("\npublic ").append(kind).append(' ').append(simple).append(typeParams).append(extendsClause).append(" {\n")
                .append(body).append("}\n");
        var referenced = new LinkedHashSet<>(types.referenced());
        if (info.superName() != null) {
            referenced.add(info.superName().replace('/', '.'));
        }
        referenced.remove(fqn);
        return new Rendered(out.toString(), referenced);
    }

    /** Hàm của lớp dữ liệu: khởi tạo, getter / setter / is, equals / hashCode / toString, builder. Hàm static không tính. */
    private static final Pattern DATA_METHOD = Pattern.compile(
            "<init>|<clinit>|(get|set|is|with)[A-Z0-9_].*|equals|hashCode|toString|canEqual|builder|toBuilder|values|valueOf|[$].*|lambda[$].*");

    /** Lớp chỉ chứa dữ liệu (DTO, enum, lớp bọc kết quả) — không có hàm nghiệp vụ. */
    private static boolean dataLike(ClassInfo info) {
        return info.methods().stream()
                .filter(m -> (m.access() & ClassFileReader.ACC_STATIC) == 0 || m.name().startsWith("<"))
                .allMatch(m -> DATA_METHOD.matcher(m.name()).matches());
    }

    private static boolean isTrivialSuper(String name) {
        String n = name.replace('/', '.');
        return n.equals("Object") || n.equals("java.lang.Object") || n.equals("java.lang.Record") || n.equals("Record")
                || n.equals("java.lang.Enum") || n.startsWith("Enum<");
    }

    private static String field(Field f, StubTypes types, boolean isEnum) {
        boolean constant = (f.access() & ClassFileReader.ACC_ENUM) != 0;
        if (constant || (f.access() & (ClassFileReader.ACC_STATIC | ClassFileReader.ACC_SYNTHETIC)) != 0 || f.name().contains("$")
                || f.name().equals("serialVersionUID")) {
            return null;
        }
        String type = types.type(f.signature() != null ? f.signature() : f.descriptor());
        var line = new StringBuilder("    ");
        for (Annotation a : f.annotations()) {
            String internal = a.descriptor().substring(1, a.descriptor().length() - 1);
            String simple = internal.substring(internal.lastIndexOf('/') + 1);
            if (!KEPT_ANNOTATIONS.contains(simple)) {
                continue;
            }
            line.append('@').append(types.ref(internal)).append(elements(a)).append(' ');
        }
        return line.append("private ").append(type).append(' ').append(f.name()).append(";\n").toString();
    }

    private static String elements(Annotation a) {
        Map<String, String> kept = new LinkedHashMap<>();
        a.elements().forEach((k, v) -> {
            if (!SKIPPED_ELEMENTS.contains(k)) {
                kept.put(k, v);
            }
        });
        if (kept.isEmpty()) {
            return "";
        }
        if (kept.size() == 1 && kept.containsKey("value")) {
            return "(" + kept.get("value") + ")";
        }
        var parts = new ArrayList<String>();
        kept.forEach((k, v) -> parts.add(k + " = " + v));
        return "(" + String.join(", ", parts) + ")";
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------------- mã dự án dùng lớp nào ----------------

    /** Những gì mã dự án nói về thư viện: import, gói, các tên lớp xuất hiện, và lớp đã có mã nguồn thật. */
    private static final class Usage {
        final Set<String> imports = new HashSet<>();
        final Set<String> wildcardPackages = new HashSet<>();
        final Set<String> projectPackages = new HashSet<>();
        final Set<String> classTokens = new HashSet<>();
        final Set<String> defined = new HashSet<>();

        static Usage of(List<SourceFile> files) {
            var usage = new Usage();
            for (SourceFile file : files) {
                String base = file.relativePath().substring(file.relativePath().lastIndexOf('/') + 1);
                String pkg = "";
                Matcher p = PACKAGE.matcher(file.content());
                if (p.find()) {
                    pkg = p.group(1);
                    usage.projectPackages.add(pkg);
                }
                if (base.endsWith(".java")) {
                    usage.defined.add((pkg.isEmpty() ? "" : pkg + ".") + base.substring(0, base.length() - ".java".length()));
                }
                Matcher m = IMPORT.matcher(file.content());
                while (m.find()) {
                    if (m.group(1) != null) {
                        continue; // import static
                    }
                    (m.group(3) != null ? usage.wildcardPackages : usage.imports).add(m.group(2));
                }
                Matcher t = CLASS_TOKEN.matcher(file.content());
                while (t.find()) {
                    usage.classTokens.add(t.group());
                }
            }
            return usage;
        }

        boolean uses(String fqn) {
            if (imports.contains(fqn)) {
                return true;
            }
            int dot = fqn.lastIndexOf('.');
            String pkg = dot < 0 ? "" : fqn.substring(0, dot);
            String simple = fqn.substring(dot + 1);
            return (wildcardPackages.contains(pkg) || projectPackages.contains(pkg)) && classTokens.contains(simple);
        }
    }
}
