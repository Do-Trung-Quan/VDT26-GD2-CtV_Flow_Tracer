package com.ctv.flowtracer.core.linker;

import com.ctv.flowtracer.ir.LinkRule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Áp luật liên kết lên đường dẫn. Không áp nối tiếp mọi luật (hai luật proxy {@code ^/gateway} và {@code ^/api} áp liên tiếp
 * sẽ cắt mất phần đường dẫn thật) mà sinh các <b>phương án</b>: đường dẫn gốc, đường dẫn sau MỘT luật, và sau một luật
 * thủ công rồi một luật tự phát hiện. Bộ so khớp thử mọi phương án; phương án càng ít luật càng được ưu tiên.
 *
 * <ul>
 *   <li>{@code PREFIX} (FE): đường dẫn bắt đầu bằng {@code pattern} (trọn đoạn) thì thay bằng {@code replacement} (rỗng =
 *       bỏ). Dùng cho tiền tố base URL và để thay phần không tính được, vd {@code {API_BASE}} → {@code /api/v1}.
 *   <li>{@code REWRITE} (FE): biểu thức chính quy {@code pattern} thay bằng {@code replacement} — như {@code pathRewrite}
 *       của proxy.
 *   <li>{@code CONTEXT_PATH} (BE): tiền tố thêm vào đường dẫn endpoint. Luật tự phát hiện chỉ để xem (đường dẫn endpoint
 *       đã cộng sẵn context-path khi phân tích); luật thủ công áp cho endpoint chưa có context-path.
 * </ul>
 */
public final class LinkRuleEngine {

    /** Một phương án đường dẫn và các luật đã áp để có nó. */
    public record Variant(String path, List<LinkRule> applied) {

        public Variant {
            applied = List.copyOf(applied);
        }
    }

    /** Phương án đường dẫn của một lời gọi HTTP, phương án gốc đứng đầu, không trùng đường dẫn. */
    public List<Variant> frontendVariants(String rawPath, List<LinkRule> rules) {
        List<LinkRule> manual = rules.stream()
                .filter(r -> r.origin() == LinkRule.Origin.MANUAL && isFrontend(r))
                .toList();
        List<LinkRule> auto = rules.stream()
                .filter(r -> r.origin() == LinkRule.Origin.AUTO && isFrontend(r))
                .toList();
        Map<String, Variant> variants = new LinkedHashMap<>();
        variants.put(rawPath, new Variant(rawPath, List.of()));
        for (LinkRule rule : manual) {
            apply(rule, rawPath).ifPresent(p -> variants.putIfAbsent(p, new Variant(p, List.of(rule))));
        }
        for (LinkRule rule : auto) {
            apply(rule, rawPath).ifPresent(p -> variants.putIfAbsent(p, new Variant(p, List.of(rule))));
        }
        for (LinkRule first : manual) {
            Optional<String> once = apply(first, rawPath);
            if (once.isEmpty()) {
                continue;
            }
            for (LinkRule second : auto) {
                apply(second, once.get()).ifPresent(p -> variants.putIfAbsent(p, new Variant(p, List.of(first, second))));
            }
        }
        return List.copyOf(variants.values());
    }

    /**
     * Phương án đường dẫn của một endpoint: đường dẫn gốc, và nếu endpoint chưa có context-path thì thêm phương án với
     * từng luật context-path thủ công.
     *
     * @param hasContextPath endpoint đã được cộng context-path khi phân tích
     */
    public List<Variant> backendVariants(String path, boolean hasContextPath, List<LinkRule> rules) {
        var out = new ArrayList<Variant>();
        out.add(new Variant(path, List.of()));
        if (hasContextPath) {
            return out;
        }
        for (LinkRule rule : rules) {
            if (rule.type() != LinkRule.Type.CONTEXT_PATH || rule.origin() != LinkRule.Origin.MANUAL) {
                continue;
            }
            String prefix = trimSlash(rule.pattern());
            if (prefix.isEmpty() || path.equals(prefix) || path.startsWith(prefix + "/")) {
                continue;
            }
            String joined = prefix + (path.startsWith("/") ? path : "/" + path);
            if (out.stream().noneMatch(v -> v.path().equals(joined))) {
                out.add(new Variant(joined, List.of(rule)));
            }
        }
        return out;
    }

    /** Luật có hợp lệ không (mẫu regex của REWRITE biên dịch được); trả về lý do nếu sai, rỗng nếu đúng. */
    public static Optional<String> validate(LinkRule rule) {
        if (rule.pattern().isBlank()) {
            return Optional.of("Mẫu không được để trống");
        }
        if (rule.type() == LinkRule.Type.REWRITE) {
            try {
                Pattern.compile(rule.pattern());
            } catch (PatternSyntaxException e) {
                return Optional.of("Biểu thức chính quy không hợp lệ: " + rule.pattern());
            }
        }
        return Optional.empty();
    }

    private static boolean isFrontend(LinkRule rule) {
        return rule.type() == LinkRule.Type.PREFIX || rule.type() == LinkRule.Type.REWRITE;
    }

    /** Kết quả áp một luật; rỗng nếu luật không làm đổi đường dẫn. */
    static Optional<String> apply(LinkRule rule, String path) {
        String replacement = rule.replacement() == null ? "" : rule.replacement();
        String result;
        if (rule.type() == LinkRule.Type.PREFIX) {
            String prefix = rule.pattern().endsWith("/") && rule.pattern().length() > 1
                    ? rule.pattern().substring(0, rule.pattern().length() - 1)
                    : rule.pattern();
            boolean boundary = path.equals(prefix) || path.startsWith(prefix + "/") || prefix.endsWith("/") && path.startsWith(prefix);
            if (!boundary) {
                return Optional.empty();
            }
            result = trimSlash(replacement) + path.substring(prefix.length());
        } else if (rule.type() == LinkRule.Type.REWRITE) {
            try {
                result = Pattern.compile(rule.pattern()).matcher(path).replaceFirst(replacement);
            } catch (RuntimeException e) {
                return Optional.empty(); // mẫu sai hoặc tham chiếu nhóm không có: bỏ qua luật này
            }
        } else {
            return Optional.empty();
        }
        if (result.isEmpty()) {
            result = "/";
        } else if (!result.startsWith("/") && !result.startsWith("{")) {
            result = "/" + result;
        }
        return result.equals(path) ? Optional.empty() : Optional.of(result);
    }

    /** Bỏ '/' ở cuối; luôn bắt đầu bằng '/' nếu không rỗng. */
    private static String trimSlash(String value) {
        String v = value == null ? "" : value.strip();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        if (!v.isEmpty() && !v.startsWith("/") && !v.startsWith("{")) {
            v = "/" + v;
        }
        return v;
    }
}
