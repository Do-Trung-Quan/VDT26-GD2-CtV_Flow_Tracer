package com.ctv.flowtracer.app.linking.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.linking.dto.request.LinkRuleItemRequest;
import com.ctv.flowtracer.app.linking.dto.request.UpdateLinkRulesRequest;
import com.ctv.flowtracer.app.linking.dto.response.LinkRuleResponse;
import com.ctv.flowtracer.app.linking.dto.response.LinkRulesResponse;
import com.ctv.flowtracer.app.linking.dto.response.LinkSummaryResponse;
import com.ctv.flowtracer.app.workspace.model.LinkRuleSettings;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import com.ctv.flowtracer.core.linker.LinkRuleEngine;
import com.ctv.flowtracer.ir.LinkRule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Luật liên kết (API E6–E7): gộp luật tool tự tìm thấy (proxy, context-path — đọc lại mỗi lần quét, chỉ xem) và luật người
 * dùng nhập. Lưu luật thủ công thì nối lại mọi lời gọi HTTP NGAY, không cần quét lại — trừ khi đang có lần quét chạy (lần
 * quét đó sẽ dùng luật mới ở bước LINK).
 */
@Service
public class LinkRuleService {

    private final WorkspaceService workspaceService;
    private final LinkingService linkingService;

    public LinkRuleService(WorkspaceService workspaceService, LinkingService linkingService) {
        this.workspaceService = workspaceService;
        this.linkingService = linkingService;
    }

    /** E6 */
    @Transactional(readOnly = true)
    public LinkRulesResponse get(UUID workspaceId) {
        return toResponse(workspaceService.linkRules(workspaceId), null);
    }

    /**
     * E7 — thay toàn bộ luật thủ công rồi nối lại.
     *
     * @throws AppException {@code WORKSPACE_NOT_FOUND}; {@code VALIDATION_FAILED} nếu mẫu REWRITE không phải biểu thức chính
     *     quy hợp lệ
     */
    @Transactional
    public LinkRulesResponse update(UUID workspaceId, UpdateLinkRulesRequest request) {
        workspaceService.requireExists(workspaceId);
        Map<String, Object> errors = new LinkedHashMap<>();
        Map<String, LinkRule> manual = new LinkedHashMap<>();
        for (int i = 0; i < request.manual().size(); i++) {
            LinkRuleItemRequest item = request.manual().get(i);
            LinkRule rule = new LinkRule(
                    item.type(), item.pattern().strip(), blankToNull(item.replacement()), LinkRule.Origin.MANUAL, blankToNull(item.description()));
            Optional<String> invalid = LinkRuleEngine.validate(rule);
            if (invalid.isPresent()) {
                errors.put("manual[" + i + "].pattern", invalid.get());
                continue;
            }
            manual.putIfAbsent(rule.type() + "|" + rule.pattern() + "|" + rule.replacement(), rule);
        }
        if (!errors.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Luật liên kết không hợp lệ", errors);
        }
        LinkRuleSettings saved = workspaceService.updateManualLinkRules(workspaceId, List.copyOf(manual.values()));
        if (workspaceService.scanRunning(workspaceId)) {
            return toResponse(saved, null);
        }
        return toResponse(saved, LinkSummaryResponse.from(linkingService.relink(workspaceId)));
    }

    private static LinkRulesResponse toResponse(LinkRuleSettings rules, LinkSummaryResponse relink) {
        return new LinkRulesResponse(
                rules.detected().stream().map(LinkRuleResponse::from).toList(),
                rules.manual().stream().map(LinkRuleResponse::from).toList(),
                relink);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
