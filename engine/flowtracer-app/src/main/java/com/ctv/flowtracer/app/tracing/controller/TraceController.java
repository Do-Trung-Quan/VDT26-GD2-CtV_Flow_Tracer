package com.ctv.flowtracer.app.tracing.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.tracing.dto.request.CreateTraceRequest;
import com.ctv.flowtracer.app.tracing.dto.response.TraceExportResponse;
import com.ctv.flowtracer.app.tracing.dto.response.TraceResponse;
import com.ctv.flowtracer.app.tracing.model.ExportFormat;
import com.ctv.flowtracer.app.tracing.service.TracingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API D1–D2: dò vết luồng và xuất diagram. */
@RestController
@RequestMapping("/workspaces/{id}/traces")
public class TraceController {

    private final TracingService tracingService;

    public TraceController(TracingService tracingService) {
        this.tracingService = tracingService;
    }

    /** D1 — dò vết luồng từ một API Backend; không gọi AI. */
    @PostMapping
    public ApiResponse<TraceResponse> trace(@PathVariable UUID id, @Valid @RequestBody CreateTraceRequest request) {
        return ApiResponse.ok(tracingService.trace(id, request));
    }

    /** D2 — xuất diagram của kết quả D1 ({@code format} = {@code plantuml} hoặc {@code svg}). */
    @GetMapping("/{traceId}/export")
    public ApiResponse<TraceExportResponse> export(
            @PathVariable UUID id, @PathVariable String traceId, @RequestParam String format) {
        return ApiResponse.ok(tracingService.export(id, traceId, ExportFormat.parse(format)));
    }
}
