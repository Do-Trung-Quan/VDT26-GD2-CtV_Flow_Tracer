package com.ctv.flowtracer.core.explain;

import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.ir.IrNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * API Backend + kiểu dữ liệu {@code DATA_TYPE} → khối JSON Đầu vào / Đầu ra để đọc (không phải JSON Schema): mỗi trường là một
 * câu ngắn "kiểu — bắt buộc, tối đa 50 ký tự…". Đầu ra giữ lớp bọc chung ({@code ApiResponse<T>}) nhưng mở {@code T} thành dữ
 * liệu thật. Thành phần UI lấy khối của API mà nó gọi.
 */
public final class IoSchemaBuilder {

    /** Số lỗi tối đa liệt kê. */
    private static final int MAX_ERRORS = 8;

    /**
     * Một khối Đầu vào / Đầu ra.
     *
     * @param input các phần đầu vào có dữ liệu: {@code path}, {@code query}, {@code body}; rỗng nếu API không nhận gì
     * @param output cấu trúc kết quả trả về; rỗng nếu API không trả nội dung
     * @param errors các trường hợp lỗi có thể gặp, bằng lời
     */
    public record IoSection(Map<String, Object> input, Map<String, Object> output, List<String> errors) {}

    private final Function<String, Optional<IrNode>> types;

    /** @param types tra node {@code DATA_TYPE} theo key */
    public IoSchemaBuilder(Function<String, Optional<IrNode>> types) {
        this.types = types;
    }

    public IoSection build(IrNode endpoint, GraphIndex graph) {
        Map<String, Object> input = new LinkedHashMap<>();
        Map<String, Object> path = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        boolean anyConstraint = false;
        for (Map<String, Object> p : TypeExprs.maps(endpoint.attrs().get("params"))) {
            String in = String.valueOf(p.get("in"));
            if (!in.equals("PATH") && !in.equals("QUERY")) {
                continue;
            }
            List<String> flags = new ArrayList<>();
            flags.add(Boolean.TRUE.equals(p.get("required")) ? "bắt buộc" : "không bắt buộc");
            if (p.get("defaultValue") != null) {
                flags.add("mặc định " + p.get("defaultValue"));
            }
            Object shape = describe(TypeExprs.map(p.get("typeExpr")), flags, null, true, 0);
            (in.equals("PATH") ? path : query).put(String.valueOf(p.get("name")), shape);
            anyConstraint |= Boolean.TRUE.equals(p.get("required"));
        }
        putIfNotEmpty(input, "path", path);
        putIfNotEmpty(input, "query", query);
        Map<String, Object> requestExpr = TypeExprs.map(endpoint.attrs().get("requestTypeExpr"));
        if (!requestExpr.isEmpty()) {
            input.put("body", describe(requestExpr, List.of(), null, true, 0));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        Map<String, Object> responseExpr = TypeExprs.map(endpoint.attrs().get("responseTypeExpr"));
        if (!responseExpr.isEmpty() && !"any".equals(TypeExprs.t(responseExpr))) {
            Object shape = describe(responseExpr, List.of(), null, false, 0);
            if (shape instanceof Map<?, ?> m) {
                m.forEach((k, v) -> output.put(String.valueOf(k), v));
            } else {
                output.put("data", shape);
            }
        }
        return new IoSection(input, output, errors(endpoint, graph, requestExpr, anyConstraint));
    }

    private static void putIfNotEmpty(Map<String, Object> target, String key, Map<String, Object> value) {
        if (!value.isEmpty()) {
            target.put(key, value);
        }
    }

    // ---------------- mô tả một kiểu ----------------

    /**
     * @param flags các ý bổ sung đã có cho chính giá trị này (vd "bắt buộc" của tham số)
     * @param withFlags có ghi "bắt buộc / không bắt buộc" và ràng buộc của từng trường không (đầu vào có, đầu ra không)
     */
    private Object describe(Map<String, Object> expr, List<String> flags, String description, boolean withFlags, int depth) {
        String t = TypeExprs.t(expr);
        switch (t) {
            case "ref" -> {
                return depth >= TypeExprs.MAX_DEPTH ? leaf("object", flags, description) : fieldsOf(expr, withFlags, depth);
            }
            case "array" -> {
                Object item = describe(TypeExprs.map(expr.get("items")), List.of(), null, withFlags, depth + 1);
                return item instanceof String text ? List.of(text + listNote(flags, description)) : List.of(item);
            }
            case "map" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("<khóa>", describe(TypeExprs.map(expr.get("values")), List.of(), null, withFlags, depth + 1));
                return m;
            }
            default -> {
                return leaf(typeText(expr), flags, description);
            }
        }
    }

    private Object fieldsOf(Map<String, Object> ref, boolean withFlags, int depth) {
        Optional<IrNode> found = types.apply(String.valueOf(ref.get("key")));
        if (found.isEmpty()) {
            return "object";
        }
        IrNode type = found.get();
        Map<String, Map<String, Object>> bindings = TypeExprs.bindings(type, ref);
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map<String, Object> field : TypeExprs.fields(type)) {
            Map<String, Object> ft = TypeExprs.fieldType(field, bindings);
            List<String> flags = withFlags ? fieldFlags(field, ft) : List.of();
            String description = field.get("description") instanceof String d ? d : null;
            out.put(String.valueOf(field.get("name")), describe(ft, flags, description, withFlags, depth + 1));
        }
        return out;
    }

    /** "string — bắt buộc, tối đa 50 ký tự. Mô tả của trường." */
    private static String leaf(String type, List<String> flags, String description) {
        var text = new StringBuilder(type);
        if (!flags.isEmpty()) {
            text.append(" — ").append(String.join(", ", flags));
        }
        if (description != null && !description.isBlank()) {
            text.append(flags.isEmpty() ? " — " : ". ").append(description.strip());
        }
        return text.toString();
    }

    private static String listNote(List<String> flags, String description) {
        return flags.isEmpty() && description == null ? "" : " (" + leaf("danh sách", flags, description).replaceFirst("^danh sách( — )?", "") + ")";
    }

    private static String typeText(Map<String, Object> expr) {
        return switch (TypeExprs.t(expr)) {
            case "date" -> "string (ngày yyyy-MM-dd)";
            case "datetime" -> "string (ngày giờ ISO-8601)";
            case "uuid" -> "string (UUID)";
            case "enum" -> "string (một trong: " + (expr.get("values") instanceof List<?> l ? String.join(", ", l.stream().map(String::valueOf).toList()) : "") + ")";
            case "object" -> "object";
            case "file" -> "file";
            case "any", "var" -> "any";
            default -> TypeExprs.t(expr);
        };
    }

    // ---------------- ràng buộc của một trường → câu ngắn ----------------

    static List<String> fieldFlags(Map<String, Object> field, Map<String, Object> type) {
        var flags = new ArrayList<String>();
        flags.add(TypeExprs.required(field) ? "bắt buộc" : "không bắt buộc");
        String unit = "array".equals(TypeExprs.t(type)) ? "phần tử" : "ký tự";
        for (Map<String, Object> c : TypeExprs.maps(field.get("constraints"))) {
            switch (String.valueOf(c.get("kind"))) {
                case "size" -> {
                    Object min = c.get("min");
                    Object max = c.get("max");
                    if (min != null && max != null) {
                        flags.add("từ " + min + " đến " + max + " " + unit);
                    } else if (max != null) {
                        flags.add("tối đa " + max + " " + unit);
                    } else if (min != null) {
                        flags.add("tối thiểu " + min + " " + unit);
                    }
                }
                case "min" -> flags.add("giá trị tối thiểu " + c.get("value"));
                case "max" -> flags.add("giá trị tối đa " + c.get("value"));
                case "positive" -> flags.add("phải là số dương");
                case "nonNegative" -> flags.add("không âm");
                case "pattern" -> flags.add("đúng mẫu " + c.get("value"));
                case "email" -> flags.add("đúng định dạng email");
                case "unique" -> flags.add("không được trùng");
                case "past" -> flags.add("là thời điểm trong quá khứ");
                case "future" -> flags.add("là thời điểm trong tương lai");
                default -> {
                    // notBlank đã nằm trong "bắt buộc"
                }
            }
        }
        return flags;
    }

    // ---------------- lỗi có thể gặp ----------------

    private List<String> errors(IrNode endpoint, GraphIndex graph, Map<String, Object> requestExpr, boolean hasRequiredParams) {
        Set<String> errors = new LinkedHashSet<>();
        BackendFlow flow = BackendFlow.of(endpoint, graph);
        for (IrNode method : flow.methods()) {
            for (Map<String, Object> thrown : TypeExprs.maps(method.attrs().get("throws"))) {
                // Câu thông báo của dự án; chưa tra được thì nêu mã lỗi (không in nguyên biểu thức throw)
                errors.add(thrown.get("message") instanceof String m
                        ? lowerFirst(m)
                        : thrown.get("messageKey") instanceof String k ? (k.contains(" ") ? lowerFirst(k) : "lỗi " + k) :"lỗi nghiệp vụ (" + thrown.get("exception") + ")");
            }
        }
        if (hasRequiredParams || hasRequiredBodyField(requestExpr)) {
            errors.add("thiếu trường bắt buộc hoặc sai định dạng");
        }
        if (flow.methods().stream().anyMatch(m -> m.attrs().get("preAuthorize") != null)) {
            errors.add("không đủ quyền");
        }
        return errors.stream().limit(MAX_ERRORS).toList();
    }

    private boolean hasRequiredBodyField(Map<String, Object> requestExpr) {
        if (!"ref".equals(TypeExprs.t(requestExpr))) {
            return false;
        }
        return types.apply(String.valueOf(requestExpr.get("key")))
                .map(t -> TypeExprs.fields(t).stream().anyMatch(TypeExprs::required))
                .orElse(false);
    }

    /** Hạ chữ hoa đầu của câu thông báo; giữ nguyên khóa viết hoa toàn bộ (CATEGORY_CODE_EXISTS). */
    private static String lowerFirst(String text) {
        if (text.isEmpty() || text.equals(text.toUpperCase(Locale.ROOT))) {
            return text;
        }
        return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
    }
}
