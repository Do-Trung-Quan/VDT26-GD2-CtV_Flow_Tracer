package com.ctv.flowtracer.ir.spi;

import com.ctv.flowtracer.ir.LinkRule;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ngữ cảnh pha 2 (resolve) cho một Source.
 *
 * @param sourceId id Source trong CSDL
 * @param allPaths toàn bộ file của Source
 * @param loader đọc nội dung file cấu hình khi cần (application.yml, proxy.conf.json…)
 * @param manualRules luật do người dùng nhập (vd QUERY_SINK cho thư viện truy vấn dạng jar)
 * @param options tùy chọn theo adapter
 */
public record ResolveContext(
        String sourceId,
        List<String> allPaths,
        SourceContentLoader loader,
        List<LinkRule> manualRules,
        Map<String, String> options) {

    public ResolveContext {
        Objects.requireNonNull(sourceId, "sourceId");
        allPaths = allPaths == null ? List.of() : List.copyOf(allPaths);
        Objects.requireNonNull(loader, "loader");
        manualRules = manualRules == null ? List.of() : List.copyOf(manualRules);
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
