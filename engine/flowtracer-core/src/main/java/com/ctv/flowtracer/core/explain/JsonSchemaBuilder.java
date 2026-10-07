package com.ctv.flowtracer.core.explain;

import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.ir.IrNode;
import java.util.Map;

/** DATA_TYPE → JSON Schema (để AI sinh dữ liệu mẫu đúng cấu trúc và để kiểm tra kết quả). */
public final class JsonSchemaBuilder {

    /** @return JSON Schema dạng Map (draft 2020-12) */
    public Map<String, Object> build(IrNode dataType, GraphIndex graph) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
