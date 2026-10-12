package com.ctv.flowtracer.adapter.spring.config;

import com.ctv.flowtracer.ir.Diagnostic;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chỉ đọc đúng các khóa cần thiết trong {@code application.yml/.yaml/.properties} mặc định (không theo profile) của từng
 * module: {@code server.servlet.context-path} và {@code spring.jackson.property-naming-strategy}. KHÔNG giữ giá trị bí
 * mật — các khóa khác bị bỏ qua ngay khi đọc.
 */
public final class ApplicationConfigReader {

    static final String CONTEXT_PATH_KEY = "server.servlet.context-path";
    static final String JSON_NAMING_KEY = "spring.jackson.property-naming-strategy";

    private static final Pattern CONFIG_FILE =
            Pattern.compile("^(?:(.*)/)?src/main/resources/application\\.(yml|yaml|properties)$");
    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([^:}]+)(?::([^}]*))?}$");

    /**
     * Cấu hình của một module.
     *
     * @param moduleRoot thư mục module tương đối trong Source ({@code ""} là gốc Source)
     * @param contextPath context-path đã chuẩn hóa ({@code ""} nếu không khai báo hoặc không xác định được)
     * @param jsonNamingStrategy chiến lược đặt tên JSON (null nếu không khai báo)
     * @param configFile file cấu hình đã đọc
     */
    public record AppConfig(String moduleRoot, String contextPath, String jsonNamingStrategy, String configFile) {}

    /** Kết quả đọc: cấu hình từng module (module sâu nhất trước) và cảnh báo. */
    public record Result(List<AppConfig> configs, List<Diagnostic> diagnostics) {

        /** Cấu hình áp cho một file: module có thư mục gốc chứa file, sâu nhất. */
        public Optional<AppConfig> forFile(String relativePath) {
            return configs.stream()
                    .filter(c -> c.moduleRoot().isEmpty() || relativePath.startsWith(c.moduleRoot() + "/"))
                    .findFirst();
        }
    }

    public Result read(ResolveContext ctx) {
        var configs = new ArrayList<AppConfig>();
        var diagnostics = new ArrayList<Diagnostic>();
        for (String path : ctx.allPaths().stream().sorted().toList()) {
            Matcher m = CONFIG_FILE.matcher(path);
            if (!m.matches()) {
                continue;
            }
            String moduleRoot = m.group(1) == null ? "" : m.group(1);
            Optional<String> content = ctx.loader().load(path);
            if (content.isEmpty()) {
                continue;
            }
            boolean yaml = !path.endsWith(".properties");
            String rawContextPath = yaml ? yamlValue(content.get(), CONTEXT_PATH_KEY) : propertiesValue(content.get(), CONTEXT_PATH_KEY);
            String naming = yaml ? yamlValue(content.get(), JSON_NAMING_KEY) : propertiesValue(content.get(), JSON_NAMING_KEY);
            String contextPath = "";
            if (rawContextPath != null) {
                Optional<String> resolved = resolvePlaceholder(rawContextPath);
                if (resolved.isPresent()) {
                    contextPath = normalizeContextPath(resolved.get());
                } else {
                    diagnostics.add(new Diagnostic(
                            Diagnostic.Severity.WARNING,
                            path,
                            null,
                            "context-path lấy từ biến môi trường không có giá trị mặc định (" + rawContextPath
                                    + "); đường dẫn API được hiển thị không kèm context-path"));
                }
            }
            configs.add(new AppConfig(moduleRoot, contextPath, naming, path));
        }
        configs.sort(Comparator.comparingInt((AppConfig c) -> c.moduleRoot().length()).reversed()
                .thenComparing(AppConfig::moduleRoot));
        return new Result(List.copyOf(configs), List.copyOf(diagnostics));
    }

    /** {@code ${VAR:mặc định}} → mặc định; {@code ${VAR}} → rỗng (không xác định); giá trị thường → giữ nguyên. */
    static Optional<String> resolvePlaceholder(String value) {
        Matcher m = PLACEHOLDER.matcher(value.strip());
        if (!m.matches()) {
            return Optional.of(value.strip());
        }
        return Optional.ofNullable(m.group(2));
    }

    /** {@code api/} → {@code /api}; {@code /} → {@code ""}. */
    static String normalizeContextPath(String value) {
        String v = value.strip();
        if (v.isEmpty() || v.equals("/")) {
            return "";
        }
        if (!v.startsWith("/")) {
            v = "/" + v;
        }
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    /** Đọc một khóa (dạng a.b.c) trong file .properties; khóa cuối cùng thắng như Spring. */
    static String propertiesValue(String content, String key) {
        String found = null;
        for (String raw : withoutBom(content).split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int sep = indexOfSeparator(line);
            if (sep > 0 && line.substring(0, sep).strip().equals(key)) {
                found = line.substring(sep + 1).strip();
            }
        }
        return found;
    }

    /**
     * Đọc một khóa (dạng a.b.c) trong YAML: hỗ trợ dạng lồng nhau theo thụt lề và dạng phẳng {@code a.b.c: x}; chỉ đọc
     * tài liệu YAML đầu tiên (trước {@code ---}). Không hỗ trợ cú pháp YAML phức tạp (anchor, list).
     */
    static String yamlValue(String content, String key) {
        Deque<String[]> stack = new ArrayDeque<>(); // {indent, tên khóa}
        for (String raw : withoutBom(content).split("\\R")) {
            if (raw.strip().equals("---")) {
                break;
            }
            String noComment = stripYamlComment(raw);
            if (noComment.isBlank() || noComment.strip().startsWith("-")) {
                continue;
            }
            int indent = noComment.length() - noComment.stripLeading().length();
            String line = noComment.strip();
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            while (!stack.isEmpty() && Integer.parseInt(stack.peek()[0]) >= indent) {
                stack.pop();
            }
            String name = unquote(line.substring(0, colon).strip());
            String value = line.substring(colon + 1).strip();
            String fullKey = prefix(stack) + name;
            if (value.isEmpty()) {
                stack.push(new String[] {String.valueOf(indent), name});
            } else if (fullKey.equals(key)) {
                return unquote(value);
            }
        }
        return null;
    }

    /** File lưu UTF-8 có BOM (vd từ Notepad) bắt đầu bằng U+FEFF; không bỏ thì khóa đầu tiên bị đọc sai tên. */
    static String withoutBom(String content) {
        return content.startsWith("\uFEFF") ? content.substring(1) : content;
    }

    private static String prefix(Deque<String[]> stack) {
        var parts = new ArrayList<String>();
        stack.descendingIterator().forEachRemaining(e -> parts.add(e[1]));
        return parts.isEmpty() ? "" : String.join(".", parts) + ".";
    }

    private static String stripYamlComment(String line) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static int indexOfSeparator(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '=' || c == ':') {
                return i;
            }
        }
        return -1;
    }
}
