package com.ctv.flowtracer.core.explain;

import com.ctv.flowtracer.ir.IrNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Đọc "biểu thức kiểu" do bộ phân tích ghi (xem {@code DtoSchemaBuilder} của adapter Spring): {@code t} = {@code string},
 * {@code integer}, {@code number}, {@code boolean}, {@code date}, {@code datetime}, {@code uuid}, {@code file}, {@code any},
 * {@code enum} ({@code values}), {@code array} ({@code items}), {@code map} ({@code values}), {@code ref} ({@code key},
 * {@code args}), {@code var} ({@code name}), {@code object} ({@code name}). Lõi chỉ đọc biểu thức này, không đọc tên kiểu Java.
 */
final class TypeExprs {

    /** Giới hạn độ sâu khi mở rộng kiểu lồng nhau (chặn đệ quy vòng và kết quả quá dài). */
    static final int MAX_DEPTH = 5;

    private TypeExprs() {}

    static String t(Map<String, Object> expr) {
        return String.valueOf(expr.get("t"));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        var out = new ArrayList<Map<String, Object>>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    /** Trường của một node {@code DATA_TYPE}, theo thứ tự khai báo. */
    static List<Map<String, Object>> fields(IrNode type) {
        return maps(type.attrs().get("fields"));
    }

    /**
     * Gắn đối số generic của một biểu thức {@code ref} vào tham số kiểu của node: {@code ApiResponse<CategoryResponse>} cho
     * {@code T → CategoryResponse}. Thiếu đối số thì tham số đó không được gắn (trường kiểu {@code T} coi như {@code any}).
     */
    static Map<String, Map<String, Object>> bindings(IrNode type, Map<String, Object> ref) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        List<?> names = type.attrs().get("typeParameters") instanceof List<?> l ? l : List.of();
        List<Map<String, Object>> args = maps(ref.get("args"));
        for (int i = 0; i < names.size() && i < args.size(); i++) {
            out.put(String.valueOf(names.get(i)), args.get(i));
        }
        return out;
    }

    /** Thay {@code var} bằng đối số đã gắn; {@code var} chưa gắn trở thành {@code any}. */
    static Map<String, Object> bind(Map<String, Object> expr, Map<String, Map<String, Object>> bindings) {
        if ("var".equals(t(expr))) {
            return bindings.getOrDefault(String.valueOf(expr.get("name")), Map.of("t", "any"));
        }
        Map<String, Object> copy = new TreeMap<>();
        expr.forEach((k, v) -> {
            if (v instanceof Map<?, ?>) {
                copy.put(k, bind(map(v), bindings));
            } else if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
                copy.put(k, maps(list).stream().map(x -> (Object) bind(x, bindings)).toList());
            } else {
                copy.put(k, v);
            }
        });
        return copy;
    }

    /** Kiểu của trường đã thay tham số kiểu bằng đối số của {@code ref} chứa nó. */
    static Map<String, Object> fieldType(Map<String, Object> field, Map<String, Map<String, Object>> bindings) {
        return bind(map(field.get("type")), bindings);
    }

    /** Ràng buộc {@code kind} của trường (nếu có). */
    static Optional<Map<String, Object>> constraint(Map<String, Object> field, String kind) {
        return maps(field.get("constraints")).stream().filter(c -> kind.equals(c.get("kind"))).findFirst();
    }

    static boolean required(Map<String, Object> field) {
        return Boolean.TRUE.equals(field.get("required"));
    }
}
