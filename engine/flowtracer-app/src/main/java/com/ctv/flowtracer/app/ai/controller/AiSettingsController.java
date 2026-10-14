package com.ctv.flowtracer.app.ai.controller;

import com.ctv.flowtracer.app.ai.dto.request.UpdateAiSettingsRequest;
import com.ctv.flowtracer.app.ai.dto.response.AiSettingsResponse;
import com.ctv.flowtracer.app.ai.dto.response.AiTestResponse;
import com.ctv.flowtracer.app.ai.dto.response.AiUsageResponse;
import com.ctv.flowtracer.app.ai.service.AiSettingsService;
import com.ctv.flowtracer.app.common.web.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API I1–I3, I6: cấu hình AI, bật / tắt mục đích, kiểm tra kết nối, thống kê sử dụng. */
@RestController
@RequestMapping
public class AiSettingsController {

    private final AiSettingsService service;

    public AiSettingsController(AiSettingsService service) {
        this.service = service;
    }

    /** I1 — không bao giờ trả khóa API. */
    @GetMapping("/settings/ai")
    public ApiResponse<AiSettingsResponse> get() {
        return ApiResponse.ok(service.view());
    }

    /** I2 — chỉ bật / tắt mục đích. */
    @PatchMapping("/settings/ai")
    public ApiResponse<AiSettingsResponse> update(@Valid @RequestBody UpdateAiSettingsRequest request) {
        return ApiResponse.ok(service.update(request));
    }

    /** I3 — gửi một yêu cầu ngắn tới provider đang cấu hình. */
    @PostMapping("/settings/ai/test")
    public ApiResponse<AiTestResponse> test() {
        return ApiResponse.ok(service.test());
    }

    /** I6 — mặc định 7 ngày gần nhất. */
    @GetMapping("/ai/usage")
    public ApiResponse<AiUsageResponse> usage(
            @RequestParam(name = "from_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "workspace_id", required = false) UUID workspaceId) {
        return ApiResponse.ok(service.usage(from, to, workspaceId));
    }
}
