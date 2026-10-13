package com.ctv.flowtracer.app.linking.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.linking.dto.request.CreateLinkOverrideRequest;
import com.ctv.flowtracer.app.linking.dto.response.LinkOverrideResponse;
import com.ctv.flowtracer.app.linking.entity.OverrideDecision;
import com.ctv.flowtracer.app.linking.service.LinkOverrideService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API E3–E5: liên kết người dùng chốt tay. */
@RestController
@RequestMapping("/workspaces/{id}/link-overrides")
public class LinkOverrideController {

    private final LinkOverrideService overrideService;

    public LinkOverrideController(LinkOverrideService overrideService) {
        this.overrideService = overrideService;
    }

    /** E3 */
    @GetMapping
    public ApiResponse<List<LinkOverrideResponse>> list(@PathVariable UUID id) {
        return ApiResponse.ok(overrideService.list(id));
    }

    /** E4 — áp dụng ngay cho các lần dò vết sau và giữ qua mọi lần quét. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LinkOverrideResponse> create(@PathVariable UUID id, @Valid @RequestBody CreateLinkOverrideRequest request) {
        return ApiResponse.created(
                overrideService.create(id, request),
                request.decision() == OverrideDecision.PIN ? "Đã chốt tay liên kết" : "Đã loại bỏ liên kết");
    }

    /** E5 — liên kết quay về kết quả tự động. */
    @DeleteMapping("/{overrideId}")
    public ApiResponse<Void> delete(@PathVariable UUID id, @PathVariable UUID overrideId) {
        overrideService.delete(id, overrideId);
        return ApiResponse.ok(null, "Đã bỏ liên kết chốt tay");
    }
}
