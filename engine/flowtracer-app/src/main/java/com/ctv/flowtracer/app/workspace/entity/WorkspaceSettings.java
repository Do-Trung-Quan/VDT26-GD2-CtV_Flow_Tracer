package com.ctv.flowtracer.app.workspace.entity;

import com.ctv.flowtracer.ir.LinkRule;
import com.ctv.flowtracer.ir.NoiseFilter;
import java.util.List;

/**
 * Thiết lập riêng của Workspace, lưu ở cột {@code workspaces.settings} (JSONB).
 *
 * @param manualLinkRules luật liên kết người dùng nhập
 * @param detectedLinkRules luật tự phát hiện ở lần quét gần nhất
 * @param noiseFilters bộ lọc nhiễu thủ công (cộng thêm vào mặc định của adapter)
 * @param defaultMaxDepth độ sâu dò vết mặc định (null = theo cấu hình chung)
 * @param specDocGlobs glob tài liệu đặc tả trong repo (nguồn "Mục đích" trước khi gọi AI)
 */
public record WorkspaceSettings(
        List<LinkRule> manualLinkRules,
        List<LinkRule> detectedLinkRules,
        List<NoiseFilter> noiseFilters,
        Integer defaultMaxDepth,
        List<String> specDocGlobs) {

    public WorkspaceSettings {
        manualLinkRules = manualLinkRules == null ? List.of() : List.copyOf(manualLinkRules);
        detectedLinkRules = detectedLinkRules == null ? List.of() : List.copyOf(detectedLinkRules);
        noiseFilters = noiseFilters == null ? List.of() : List.copyOf(noiseFilters);
        specDocGlobs = specDocGlobs == null ? List.of() : List.copyOf(specDocGlobs);
    }

    public static WorkspaceSettings empty() {
        return new WorkspaceSettings(List.of(), List.of(), List.of(), null, List.of());
    }
}
