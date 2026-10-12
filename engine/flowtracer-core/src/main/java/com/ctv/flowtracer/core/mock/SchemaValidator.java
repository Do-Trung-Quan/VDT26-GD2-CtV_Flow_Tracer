package com.ctv.flowtracer.core.mock;

import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kiểm tra một JSON có khớp JSON Schema (draft 2020-12) không — dùng cho kết quả sidecar (contracts/) và dữ liệu mẫu do AI
 * sinh. Dùng chung một bộ đọc JSON; không giữ trạng thái.
 */
public final class SchemaValidator {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * @param schemaJson nội dung JSON Schema
     * @param json nội dung JSON cần kiểm tra
     * @return danh sách lỗi (mô tả ngắn, có vị trí); rỗng nghĩa là hợp lệ. JSON không đọc được cũng là một lỗi.
     * @throws IllegalArgumentException khi chính schema không đọc được hoặc không hợp lệ
     */
    public List<String> validate(String schemaJson, String json) {
        Schema schema = compile(schemaJson);
        return validate(schema, json);
    }

    /** Biên dịch schema một lần để kiểm tra nhiều JSON (schema lớn, kiểm tra lặp lại). */
    public Schema compile(String schemaJson) {
        try {
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(schemaJson);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("JSON Schema không hợp lệ: " + e.getMessage(), e);
        }
    }

    public List<String> validate(Schema schema, String json) {
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (RuntimeException e) {
            return List.of("Không phải JSON hợp lệ: " + firstLine(e.getMessage()));
        }
        return schema.validate(node).stream().map(error -> error.getInstanceLocation() + ": " + error.getMessage()).sorted().toList();
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int end = message.indexOf('\n');
        return end < 0 ? message : message.substring(0, end);
    }
}
