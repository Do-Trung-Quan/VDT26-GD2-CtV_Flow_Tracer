package com.ctv.flowtracer.core.explain;

import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.ir.IrNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Ràng buộc "thô" (tất định) của một API, mỗi ràng buộc một câu — đây là danh sách hiển thị khi AI tắt, và là căn cứ để AI viết
 * gọn lại sau này (mỗi câu AI viết phải trỏ về một ràng buộc thô có thật). Bốn nguồn:
 *
 * <ol>
 *   <li>quyền: {@code @PreAuthorize} của hàm xử lý và các hàm nó gọi;
 *   <li>dữ liệu đầu vào: Bean Validation trên tham số và trên từng trường của thân yêu cầu (kể cả trường lồng nhau);
 *   <li>nhánh lỗi: mỗi {@code throw} kèm điều kiện bao quanh và thông báo (tra từ file thông báo của dự án);
 *   <li>trùng lặp: cột {@code unique} của bảng mà luồng ghi vào.
 * </ol>
 */
public final class ConstraintsExtractor {

    private static final ConditionTextRule CONDITIONS = new ConditionTextRule();

    private final Function<String, Optional<IrNode>> types;

    /** @param types tra node {@code DATA_TYPE} theo key */
    public ConstraintsExtractor(Function<String, Optional<IrNode>> types) {
        this.types = types;
    }

    public List<String> extract(IrNode endpoint, GraphIndex graph) {
        Set<String> out = new LinkedHashSet<>();
        BackendFlow flow = BackendFlow.of(endpoint, graph);
        for (IrNode method : flow.methods()) {
            if (method.attrs().get("preAuthorize") instanceof String auth) {
                out.add("Cần quyền: " + auth);
            }
        }
        for (Map<String, Object> p : TypeExprs.maps(endpoint.attrs().get("params"))) {
            String in = String.valueOf(p.get("in"));
            if ((in.equals("PATH") || in.equals("QUERY")) && Boolean.TRUE.equals(p.get("required"))) {
                out.add("Tham số " + p.get("name") + " (" + (in.equals("PATH") ? "đường dẫn" : "query") + ") là bắt buộc");
            }
        }
        collectFields(TypeExprs.map(endpoint.attrs().get("requestTypeExpr")), "", out, new java.util.HashSet<>(), 0);
        for (IrNode method : flow.methods()) {
            for (Map<String, Object> thrown : TypeExprs.maps(method.attrs().get("throws"))) {
                out.add(throwText(thrown));
            }
        }
        for (IrNode table : flow.writtenTables()) {
            if (table.attrs().get("uniqueColumns") instanceof List<?> columns && !columns.isEmpty()) {
                out.add("Cột " + String.join(", ", columns.stream().map(String::valueOf).toList()) + " của bảng "
                        + table.attrs().getOrDefault("name", table.name()) + " không được trùng");
            }
        }
        return List.copyOf(out);
    }

    /** "Nếu categoryRepository.existsByCode(code) thì báo lỗi “Mã danh mục đã tồn tại”." */
    private static String throwText(Map<String, Object> thrown) {
        String key = thrown.get("messageKey") instanceof String k ? k : null;
        String message = thrown.get("message") instanceof String m ? m : null;
        // Có câu thông báo thì chỉ nêu câu đó; chưa tra được thì nêu mã lỗi để người đọc còn tìm lại trong code
        String what = message != null ? "“" + message + "”" : key != null ? "mã lỗi " + key : "một lỗi (" + thrown.get("exception") + ")";
        String guard = thrown.get("guardText") instanceof String g && !g.isBlank() ? CONDITIONS.describe(g) : null;
        String type = String.valueOf(thrown.get("guardType"));
        if (guard == null) {
            return "Báo lỗi " + what;
        }
        return switch (type) {
            case "ELSE" -> "Nếu không (" + guard + ") thì báo lỗi " + what;
            case "CATCH" -> "Khi gặp lỗi " + guard + " thì báo lỗi " + what;
            case "LOOP", "SWITCH", "TRY" -> "Trong " + guard + ", báo lỗi " + what;
            default -> "Nếu " + guard + " thì báo lỗi " + what;
        };
    }

    /** Ràng buộc của từng trường thân yêu cầu, "code: bắt buộc, tối đa 50 ký tự"; trường lồng nhau có tiền tố "address.city". */
    private void collectFields(Map<String, Object> expr, String prefix, Set<String> out, Set<String> opening, int depth) {
        String t = TypeExprs.t(expr);
        if ("array".equals(t)) {
            collectFields(TypeExprs.map(expr.get("items")), prefix, out, opening, depth);
            return;
        }
        if (!"ref".equals(t) || depth >= TypeExprs.MAX_DEPTH) {
            return;
        }
        String key = String.valueOf(expr.get("key"));
        Optional<IrNode> found = types.apply(key);
        if (found.isEmpty() || !opening.add(key)) {
            return;
        }
        IrNode type = found.get();
        Map<String, Map<String, Object>> bindings = TypeExprs.bindings(type, expr);
        for (Map<String, Object> field : TypeExprs.fields(type)) {
            Map<String, Object> ft = TypeExprs.fieldType(field, bindings);
            String name = prefix + field.get("name");
            List<String> flags = new ArrayList<>(IoSchemaBuilder.fieldFlags(field, ft));
            flags.remove("không bắt buộc");
            if (!flags.isEmpty()) {
                out.add("Trường " + name + ": " + String.join(", ", flags));
            }
            collectFields(ft, name + ".", out, opening, depth + 1);
        }
        opening.remove(key);
    }
}
