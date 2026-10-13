package com.ctv.flowtracer.app.linking.dto.response;

import java.util.List;

/**
 * Luật liên kết của Workspace (API E6–E7).
 *
 * @param detected luật tự phát hiện ở lần quét gần nhất (chỉ xem)
 * @param manual luật thủ công, theo thứ tự ưu tiên
 * @param relink kết quả nối lại ngay sau khi lưu (chỉ có ở E7)
 */
public record LinkRulesResponse(
        List<LinkRuleResponse> detected,
        List<LinkRuleResponse> manual,
        LinkSummaryResponse relink) {}
