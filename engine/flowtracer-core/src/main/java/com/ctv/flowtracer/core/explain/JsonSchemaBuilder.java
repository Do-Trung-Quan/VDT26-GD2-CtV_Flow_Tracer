package com.ctv.flowtracer.core.explain;

import com.ctv.flowtracer.ir.IrNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Biểu thức kiểu (kiểu dữ liệu vào / ra của một API) → JSON Schema draft 2020-12, để kiểm tra dữ liệu mẫu và để AI sinh dữ liệu
 * đúng cấu trúc (Giai đoạn 7). Kiểu lồng nhau được mở tới {@value TypeExprs#MAX_DEPTH} cấp; gặp lại chính kiểu đang mở thì
 * dừng ở {@code {"type": "object"}} để không đệ quy vô hạn.
 */
public final class JsonSchemaBuilder {

    public static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";

    private final Function<String, Optional<IrNode>> types;

    /** @param types tra node {@code DATA_TYPE} theo key */
    public JsonSchemaBuilder(Function<String, Optional<IrNode>> types) {
        this.types = types;
    }

    /** @return JSON Schema đầy đủ (có {@code $schema}) của một biểu thức kiểu */
    public Map<String, Object> build(Map<String, Object> typeExpr) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", DIALECT);
        schema.putAll(of(typeExpr, new HashSet<>(), 0));
        return schema;
    }

    /** Schema của một thân yêu cầu / phản hồi gồm nhiều phần (tham số đường dẫn, query, body): {@code properties} theo tên phần. */
    public Map<String, Object> buildObject(Map<String, Map<String, Object>> propertiesByName, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", DIALECT);
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        propertiesByName.forEach((name, expr) -> props.put(name, of(expr, new HashSet<>(), 0)));
        schema.put("properties", props);
        if (!required.isEmpty()) {
            schema.put("required", List.copyOf(required));
        }
        return schema;
    }

    private Map<String, Object> of(Map<String, Object> expr, Set<String> opening, int depth) {
        Map<String, Object> s = new LinkedHashMap<>();
        switch (TypeExprs.t(expr)) {
            case "string" -> s.put("type", "string");
            case "integer" -> s.put("type", "integer");
            case "number" -> s.put("type", "number");
            case "boolean" -> s.put("type", "boolean");
            case "date" -> {
                s.put("type", "string");
                s.put("format", "date");
            }
            case "datetime" -> {
                s.put("type", "string");
                s.put("format", "date-time");
            }
            case "uuid" -> {
                s.put("type", "string");
                s.put("format", "uuid");
            }
            case "file" -> {
                s.put("type", "string");
                s.put("description", "Nội dung file");
            }
            case "enum" -> {
                s.put("type", "string");
                s.put("enum", expr.get("values") instanceof List<?> l ? List.copyOf(l) : List.of());
            }
            case "array" -> {
                s.put("type", "array");
                s.put("items", of(TypeExprs.map(expr.get("items")), opening, depth + 1));
            }
            case "map" -> {
                s.put("type", "object");
                s.put("additionalProperties", of(TypeExprs.map(expr.get("values")), opening, depth + 1));
            }
            case "ref" -> object(expr, opening, depth, s);
            case "object" -> {
                s.put("type", "object");
                if (expr.get("name") != null) {
                    s.put("title", expr.get("name"));
                }
            }
            default -> {
                // any / var chưa gắn: không ràng buộc
            }
        }
        return s;
    }

    private void object(Map<String, Object> ref, Set<String> opening, int depth, Map<String, Object> s) {
        String key = String.valueOf(ref.get("key"));
        Optional<IrNode> found = types.apply(key);
        s.put("type", "object");
        if (found.isEmpty() || depth >= TypeExprs.MAX_DEPTH || !opening.add(key)) {
            return;
        }
        IrNode type = found.get();
        s.put("title", type.name());
        Map<String, Map<String, Object>> bindings = TypeExprs.bindings(type, ref);
        Map<String, Object> props = new LinkedHashMap<>();
        var required = new ArrayList<String>();
        for (Map<String, Object> field : TypeExprs.fields(type)) {
            String name = String.valueOf(field.get("name"));
            Map<String, Object> fs = of(TypeExprs.fieldType(field, bindings), opening, depth + 1);
            constrain(field, fs);
            if (field.get("description") instanceof String d) {
                fs.put("description", d);
            }
            props.put(name, fs);
            if (TypeExprs.required(field)) {
                required.add(name);
            }
        }
        s.put("properties", props);
        if (!required.isEmpty()) {
            s.put("required", required);
        }
        opening.remove(key);
    }

    /** Ràng buộc Bean Validation → từ khóa JSON Schema tương ứng theo kiểu của trường. */
    private static void constrain(Map<String, Object> field, Map<String, Object> schema) {
        boolean text = "string".equals(schema.get("type"));
        boolean list = "array".equals(schema.get("type"));
        for (Map<String, Object> c : TypeExprs.maps(field.get("constraints"))) {
            switch (String.valueOf(c.get("kind"))) {
                case "size" -> {
                    String lo = text ? "minLength" : list ? "minItems" : null;
                    String hi = text ? "maxLength" : list ? "maxItems" : null;
                    if (lo != null && c.get("min") != null) {
                        schema.put(lo, c.get("min"));
                    }
                    if (hi != null && c.get("max") != null) {
                        schema.put(hi, c.get("max"));
                    }
                }
                case "min" -> putIfNumeric(schema, "minimum", c.get("value"));
                case "max" -> putIfNumeric(schema, "maximum", c.get("value"));
                case "positive" -> putIfNumeric(schema, "exclusiveMinimum", 0);
                case "nonNegative" -> putIfNumeric(schema, "minimum", 0);
                case "pattern" -> {
                    if (text && c.get("value") != null) {
                        schema.put("pattern", c.get("value"));
                    }
                }
                case "notBlank" -> {
                    if (text) {
                        schema.put("minLength", Math.max(1, schema.get("minLength") instanceof Number n ? n.intValue() : 1));
                    } else if (list) {
                        schema.put("minItems", Math.max(1, schema.get("minItems") instanceof Number n ? n.intValue() : 1));
                    }
                }
                case "email" -> {
                    if (text) {
                        schema.put("format", "email");
                    }
                }
                default -> {
                    // unique, past, future…: không có từ khóa tương ứng trong JSON Schema
                }
            }
        }
    }

    private static void putIfNumeric(Map<String, Object> schema, String key, Object value) {
        String type = String.valueOf(schema.get("type"));
        if (value instanceof Number && (type.equals("integer") || type.equals("number"))) {
            schema.put(key, value);
        }
    }
}
