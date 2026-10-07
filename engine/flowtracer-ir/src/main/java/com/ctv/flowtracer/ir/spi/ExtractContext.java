package com.ctv.flowtracer.ir.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ngữ cảnh pha 1 (extract) cho một Source.
 *
 * @param sourceId id Source trong CSDL (chỉ để ghi log/chẩn đoán)
 * @param allPaths toàn bộ file của Source — cho phép giải kiểu xuyên file (vd JavaSymbolSolver)
 * @param loader đọc nội dung file khác khi cần
 * @param options tùy chọn theo adapter (vd luật QUERY_SINK)
 */
public record ExtractContext(
        String sourceId, List<String> allPaths, SourceContentLoader loader, Map<String, String> options) {

    public ExtractContext {
        Objects.requireNonNull(sourceId, "sourceId");
        allPaths = allPaths == null ? List.of() : List.copyOf(allPaths);
        Objects.requireNonNull(loader, "loader");
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
