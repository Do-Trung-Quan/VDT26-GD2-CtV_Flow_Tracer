package com.ctv.flowtracer.app.linking.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.linking.dto.request.UpdateLinkRulesRequest;
import com.ctv.flowtracer.app.linking.dto.response.LinkRulesResponse;
import com.ctv.flowtracer.app.linking.service.LinkRuleService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API E6–E7: luật nối đường dẫn Frontend với Backend. */
@RestController
@RequestMapping("/workspaces/{id}/link-rules")
public class LinkRuleController {

    private final LinkRuleService ruleService;

    public LinkRuleController(LinkRuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** E6 */
    @GetMapping
    public ApiResponse<LinkRulesResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(ruleService.get(id));
    }

    /** E7 — thay toàn bộ luật thủ công và nối lại ngay. */
    @PutMapping
    public ApiResponse<LinkRulesResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateLinkRulesRequest request) {
        LinkRulesResponse result = ruleService.update(id, request);
        String message = result.relink() == null
                ? "Đã lưu luật liên kết; lần quét đang chạy sẽ áp dụng luật mới"
                : "Đã nối lại " + result.relink().changed() + " liên kết";
        return ApiResponse.ok(result, message);
    }
}
