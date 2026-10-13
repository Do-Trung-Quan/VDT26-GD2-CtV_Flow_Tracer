package com.ctv.flowtracer.core.mock;

import com.ctv.flowtracer.core.explain.JsonSchemaBuilder;
import com.ctv.flowtracer.ir.IrNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bộ sinh dữ liệu mẫu tất định — dùng khi AI tắt hoặc lỗi, và làm mốc kiểm tra cấu trúc cho dữ liệu AI sinh. Giá trị chọn theo
 * kiểu và ràng buộc của JSON Schema, và theo TÊN trường (mã → "MA001", tên / tiêu đề → "Tên mẫu", email, số điện thoại…; giá trị trung tính, không gắn với một miền nghiệp vụ nào). Không
 * dùng thời gian hiện tại hay số ngẫu nhiên: cùng schema luôn ra cùng dữ liệu ({@link #SEED} chỉ dùng để sinh UUID cố định).
 * Không đọc dữ liệu thật của dự án.
 */
public final class FallbackMockGenerator {

    /** Seed cố định để cùng schema luôn ra cùng dữ liệu. */
    public static final long SEED = 20260930L;

    private static final String DATE = "2026-10-13";
    private static final String DATE_TIME = "2026-10-13T08:30:00Z";
    private static final int MAX_DEPTH = 6;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> PATTERN_CANDIDATES = List.of("MA001", "1", "a", "abc", "0900000000", "user@example.com", DATE);

    /**
     * Dữ liệu mẫu của một API.
     *
     * @param request thân yêu cầu mẫu; không có thân thì là các tham số đường dẫn / query mẫu (có thể rỗng)
     * @param response phản hồi mẫu; null nếu API không trả nội dung
     * @param problems chỗ dữ liệu sinh ra không khớp schema (rỗng nghĩa là khớp hoàn toàn)
     */
    public record Sample(Object request, Object response, List<String> problems) {}

    /** @return giá trị mẫu thỏa schema */
    public Object generate(Map<String, Object> jsonSchema) {
        return value("", jsonSchema, 0, "$");
    }

    /**
     * Sinh dữ liệu mẫu cho một API từ cấu trúc DTO của nó, rồi kiểm tra lại bằng {@link SchemaValidator}.
     *
     * @param types tra node {@code DATA_TYPE} theo key
     */
    public Sample generate(IrNode endpoint, Function<String, Optional<IrNode>> types) {
        var schemas = new JsonSchemaBuilder(types);
        var problems = new ArrayList<String>();
        Map<String, Object> requestExpr = map(endpoint.attrs().get("requestTypeExpr"));
        Object request;
        if (!requestExpr.isEmpty()) {
            request = checked(schemas.build(requestExpr), problems, "request");
        } else {
            Map<String, Map<String, Object>> props = new LinkedHashMap<>();
            var required = new ArrayList<String>();
            for (Map<String, Object> p : maps(endpoint.attrs().get("params"))) {
                String in = String.valueOf(p.get("in"));
                if (in.equals("PATH") || in.equals("QUERY")) {
                    props.put(String.valueOf(p.get("name")), map(p.get("typeExpr")));
                    if (Boolean.TRUE.equals(p.get("required"))) {
                        required.add(String.valueOf(p.get("name")));
                    }
                }
            }
            request = props.isEmpty() ? Map.of() : checked(schemas.buildObject(props, required), problems, "request");
        }
        Map<String, Object> responseExpr = map(endpoint.attrs().get("responseTypeExpr"));
        Object response = responseExpr.isEmpty() ? null : checked(schemas.build(responseExpr), problems, "response");
        return new Sample(request, response, List.copyOf(problems));
    }

    private Object checked(Map<String, Object> schema, List<String> problems, String part) {
        Object sample = generate(schema);
        try {
            var errors = new SchemaValidator().validate(JSON.writeValueAsString(schema), JSON.writeValueAsString(sample));
            errors.forEach(e -> problems.add(part + " " + e));
        } catch (RuntimeException e) {
            problems.add(part + ": " + e.getMessage());
        }
        return sample;
    }

    // ---------------- giá trị theo schema ----------------

    @SuppressWarnings("unchecked")
    private Object value(String name, Map<String, Object> schema, int depth, String path) {
        if (depth > MAX_DEPTH) {
            return null;
        }
        if (schema.get("enum") instanceof List<?> values && !values.isEmpty()) {
            return values.get(0);
        }
        String type = String.valueOf(schema.get("type"));
        return switch (type) {
            case "string" -> text(name, schema, path);
            case "integer" -> number(name, schema, true);
            case "number" -> number(name, schema, false);
            case "boolean" -> Boolean.TRUE;
            case "array" -> {
                var list = new ArrayList<Object>();
                Object item = value(name, schema.get("items") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(), depth + 1, path + "[0]");
                int count = Math.max(1, schema.get("minItems") instanceof Number n ? n.intValue() : 1);
                for (int i = 0; i < count; i++) {
                    list.add(item);
                }
                yield list;
            }
            case "object" -> object(schema, depth, path);
            default -> "giá trị mẫu";
        };
    }

    @SuppressWarnings("unchecked")
    private Object object(Map<String, Object> schema, int depth, String path) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (schema.get("properties") instanceof Map<?, ?> props) {
            props.forEach((k, v) -> out.put(String.valueOf(k), value(String.valueOf(k), (Map<String, Object>) v, depth + 1, path + "." + k)));
        } else if (schema.get("additionalProperties") instanceof Map<?, ?> values) {
            out.put("khóa", value("", (Map<String, Object>) values, depth + 1, path + ".khóa"));
        }
        return out;
    }

    private String text(String name, Map<String, Object> schema, String path) {
        String n = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        String format = String.valueOf(schema.get("format"));
        String value;
        if (format.equals("date") || n.contains("date") && !n.contains("update")) {
            value = DATE;
        } else if (format.equals("date-time") || n.endsWith("time") || n.endsWith("at") && n.length() > 4) {
            value = DATE_TIME;
        } else if (format.equals("uuid")) {
            value = UUID.nameUUIDFromBytes((SEED + ":" + path).getBytes(StandardCharsets.UTF_8)).toString();
        } else if (format.equals("email") || n.contains("email") || n.contains("mail")) {
            value = "user@example.com";
        } else if (n.endsWith("id") && n.length() > 2 || n.equals("id")) {
            value = UUID.nameUUIDFromBytes((SEED + ":" + path).getBytes(StandardCharsets.UTF_8)).toString();
        } else if (n.contains("code") || n.endsWith("ma")) {
            value = "MA001";
        } else if (n.contains("title") || n.contains("name") || n.endsWith("ten")) {
            value = "Tên mẫu";
        } else if (n.contains("phone") || n.contains("tel") || n.contains("mobile")) {
            value = "0900000000";
        } else if (n.contains("url") || n.contains("link")) {
            value = "https://example.com";
        } else if (n.contains("address") || n.contains("diachi")) {
            value = "Địa chỉ mẫu";
        } else if (n.contains("message")) {
            value = "Thành công";
        } else if (n.contains("brief") || n.contains("desc") || n.contains("note") || n.contains("content") || n.contains("remark")) {
            value = "Mô tả mẫu";
        } else if (n.contains("user")) {
            value = "user01";
        } else {
            value = "Giá trị mẫu";
        }
        value = fit(value, schema);
        return matchPattern(value, schema);
    }

    /** Cắt / kéo dài cho vừa {@code minLength} / {@code maxLength}. */
    private static String fit(String value, Map<String, Object> schema) {
        int min = schema.get("minLength") instanceof Number n ? n.intValue() : 0;
        int max = schema.get("maxLength") instanceof Number n ? n.intValue() : Integer.MAX_VALUE;
        String v = value;
        while (v.length() < min) {
            v = v + "x";
        }
        return v.length() > max ? v.substring(0, max) : v;
    }

    /** Có {@code pattern}: thử vài giá trị quen thuộc, lấy giá trị đầu tiên khớp; không có thì giữ nguyên (sẽ báo ở {@code problems}). */
    private static String matchPattern(String value, Map<String, Object> schema) {
        if (!(schema.get("pattern") instanceof String regex)) {
            return value;
        }
        try {
            Pattern p = Pattern.compile(regex);
            if (p.matcher(value).find()) {
                return value;
            }
            return PATTERN_CANDIDATES.stream().filter(c -> p.matcher(c).find() && fits(c, schema)).findFirst().orElse(value);
        } catch (RuntimeException e) {
            return value;
        }
    }

    private static boolean fits(String candidate, Map<String, Object> schema) {
        return fit(candidate, schema).equals(candidate);
    }

    private static Object number(String name, Map<String, Object> schema, boolean integer) {
        String n = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        double v;
        if (n.contains("error") && n.contains("code")) {
            v = 0; // mã lỗi của lớp bọc kết quả: 0 = thành công
        } else if (n.equals("page")) {
            v = 1;
        } else if (n.equals("size") || n.equals("limit") || n.contains("pagesize")) {
            v = 20;
        } else if (n.contains("status") || n.contains("type") || n.endsWith("id") || n.equals("id")) {
            v = 1;
        } else if (n.contains("price") || n.contains("amount") || n.contains("gia")) {
            v = integer ? 100000 : 100000.5;
        } else {
            v = integer ? 1 : 10.5;
        }
        if (schema.get("minimum") instanceof Number min && v < min.doubleValue()) {
            v = min.doubleValue();
        }
        if (schema.get("exclusiveMinimum") instanceof Number min && v <= min.doubleValue()) {
            v = integer ? Math.floor(min.doubleValue()) + 1 : min.doubleValue() + 1;
        }
        if (schema.get("maximum") instanceof Number max && v > max.doubleValue()) {
            v = max.doubleValue();
        }
        return integer ? (Object) (long) v : (Object) v;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object o) {
        return o instanceof List<?> l ? l.stream().filter(x -> x instanceof Map).map(x -> (Map<String, Object>) x).toList() : List.of();
    }
}
