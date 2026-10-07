package com.ctv.flowtracer.core.explain;

import com.ctv.flowtracer.core.graph.GraphIndex;
import com.ctv.flowtracer.ir.IrNode;
import java.util.List;
import java.util.Map;

/** Endpoint / phần tử UI + DATA_TYPE → khối JSON Đầu vào / Đầu ra (bóc lớp bọc chung như ApiResponse<T>). */
public final class IoSchemaBuilder {

    /** Một khối Đầu vào/Đầu ra: JSON mô tả cấu trúc + danh sách lỗi có thể gặp. */
    public record IoSection(Map<String, Object> input, Map<String, Object> output, List<String> errors) {}

    public IoSection build(IrNode target, GraphIndex graph) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
