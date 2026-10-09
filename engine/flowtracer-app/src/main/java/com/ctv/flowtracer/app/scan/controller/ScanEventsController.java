package com.ctv.flowtracer.app.scan.controller;

import com.ctv.flowtracer.app.scan.service.ScanService;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API B2: luồng SSE tiến độ quét — sự kiện {@code progress} ({@code ScanProgressResponse}) trong lúc chạy và {@code done}
 * ({@code ScanRunResponse}) khi kết thúc. Luồng SSE không bọc {@code ApiResponse}.
 */
@RestController
public class ScanEventsController {

    private final ScanService scanService;

    public ScanEventsController(ScanService scanService) {
        this.scanService = scanService;
    }

    @GetMapping(path = "/scans/{scanRunId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID scanRunId) {
        return scanService.events(scanRunId);
    }
}
