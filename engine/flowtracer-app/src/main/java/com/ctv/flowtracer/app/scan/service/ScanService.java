package com.ctv.flowtracer.app.scan.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.common.sse.SseHub;
import com.ctv.flowtracer.app.common.web.PageResponse;
import com.ctv.flowtracer.app.scan.adapter.AdapterRegistry;
import com.ctv.flowtracer.app.scan.dto.request.StartScanRequest;
import com.ctv.flowtracer.app.scan.dto.response.ScanFileErrorResponse;
import com.ctv.flowtracer.app.scan.dto.response.ScanRunResponse;
import com.ctv.flowtracer.app.scan.entity.ParseStatus;
import com.ctv.flowtracer.app.scan.entity.ScanRun;
import com.ctv.flowtracer.app.scan.entity.ScanStatus;
import com.ctv.flowtracer.app.scan.entity.ScanTrigger;
import com.ctv.flowtracer.app.scan.entity.SourceFile;
import com.ctv.flowtracer.app.scan.model.ScanHandle;
import com.ctv.flowtracer.app.scan.pipeline.ProgressReporter;
import com.ctv.flowtracer.app.scan.pipeline.ScanPipeline;
import com.ctv.flowtracer.app.scan.repository.ScanRunRepository;
import com.ctv.flowtracer.app.scan.repository.SourceFileRepository;
import com.ctv.flowtracer.app.workspace.model.SourceScanTarget;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Nghiệp vụ quét (API B1–B6): mỗi Workspace tối đa một lần quét chạy cùng lúc ({@code 409}), quét chạy nền trên
 * virtual thread, hủy được; Engine khởi động lại thì lần quét dở dang được đánh dấu thất bại.
 */
@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);
    private static final Duration CANCEL_WAIT = Duration.ofSeconds(10);

    private final ScanRunRepository scanRuns;
    private final SourceFileRepository sourceFiles;
    private final RunningScanRegistry registry;
    private final ScanPipeline pipeline;
    private final ProgressReporter reporter;
    private final AdapterRegistry adapters;
    private final WorkspaceService workspaceService;
    private final ScanQueryService scanQueries;
    private final SseHub sseHub;
    private final ExecutorService executor;

    public ScanService(
            ScanRunRepository scanRuns,
            SourceFileRepository sourceFiles,
            RunningScanRegistry registry,
            ScanPipeline pipeline,
            ProgressReporter reporter,
            AdapterRegistry adapters,
            WorkspaceService workspaceService,
            ScanQueryService scanQueries,
            SseHub sseHub,
            @Qualifier("scanExecutor") ExecutorService executor) {
        this.scanRuns = scanRuns;
        this.sourceFiles = sourceFiles;
        this.registry = registry;
        this.pipeline = pipeline;
        this.reporter = reporter;
        this.adapters = adapters;
        this.workspaceService = workspaceService;
        this.scanQueries = scanQueries;
        this.sseHub = sseHub;
        this.executor = executor;
    }

    /**
     * API B1 — tạo lần quét ({@code PENDING}) và chạy nền.
     *
     * @throws AppException {@code WORKSPACE_NOT_FOUND}, {@code SCAN_IN_PROGRESS}
     */
    public synchronized ScanRunResponse start(UUID workspaceId, StartScanRequest request) {
        List<SourceScanTarget> targets = workspaceService.scanTargets(workspaceId);
        if (registry.isRunning(workspaceId)) {
            throw new AppException(
                    ErrorCode.SCAN_IN_PROGRESS, "Workspace đang có lần quét chạy, hãy chờ hoặc hủy lần quét đó",
                    Map.of("workspaceId", workspaceId.toString()));
        }
        ScanRun run = scanRuns.save(new ScanRun(workspaceId, request.trigger(), Instant.now()));
        var handle = new ScanHandle(workspaceId, run.getId());
        registry.tryRegister(handle);
        workspaceService.recordLastScan(workspaceId, run.getId());
        Function<String, StackAdapter> adapterById = id -> adapters.byId(id).orElse(null);
        executor.submit(() -> execute(handle, request.trigger(), targets, adapterById));
        return ScanRunResponse.from(run, null, false);
    }

    private void execute(
            ScanHandle handle, ScanTrigger trigger, List<SourceScanTarget> targets, Function<String, StackAdapter> adapterById) {
        ScanRunResponse result = null;
        try {
            result = pipeline.run(handle, trigger, targets, adapterById);
        } finally {
            registry.unregister(handle); // mở khóa Workspace TRƯỚC khi báo xong, để quét lại ngay không bị 409
            handle.markFinished();
            if (result != null) {
                reporter.done(result);
            }
        }
    }

    /** API B3. */
    @Transactional(readOnly = true)
    public ScanRunResponse get(UUID scanRunId) {
        ScanRun run = require(scanRunId);
        boolean cancelRequested = registry.byScanRun(scanRunId).map(ScanHandle::isCancelRequested).orElse(false);
        return ScanRunResponse.from(run, scanQueries.statsOf(run), cancelRequested);
    }

    /**
     * API B4 — yêu cầu hủy và chờ tối đa {@code CANCEL_WAIT} để lần quét dừng; kết quả của lần quét trước được giữ nguyên.
     *
     * @throws AppException {@code SCAN_NOT_RUNNING} khi lần quét đã kết thúc
     */
    public ScanRunResponse cancel(UUID scanRunId) {
        require(scanRunId);
        ScanHandle handle = registry.byScanRun(scanRunId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.SCAN_NOT_RUNNING, "Lần quét đã kết thúc, không thể hủy", Map.of("scanRunId", scanRunId.toString())));
        handle.requestCancel();
        handle.awaitFinished(CANCEL_WAIT);
        return get(scanRunId);
    }

    /** API B5 — mới nhất lên đầu. */
    @Transactional(readOnly = true)
    public PageResponse<ScanRunResponse> history(UUID workspaceId, int page, int limit) {
        workspaceService.requireExists(workspaceId);
        Page<ScanRun> result = scanRuns.findByWorkspaceIdOrderByStartedAtDesc(workspaceId, PageRequest.of(page - 1, limit));
        return new PageResponse<>(
                result.map(r -> ScanRunResponse.from(r, scanQueries.statsOf(r), false)).getContent(),
                result.getTotalElements(),
                page,
                limit);
    }

    /** API B6 — file lỗi được ghi nhận ở lần quét này, theo đường dẫn. */
    @Transactional(readOnly = true)
    public PageResponse<ScanFileErrorResponse> errors(UUID scanRunId, int page, int limit) {
        ScanRun run = require(scanRunId);
        Map<UUID, SourceScanTarget> sources = workspaceService.scanTargets(run.getWorkspaceId()).stream()
                .collect(Collectors.toMap(SourceScanTarget::sourceId, s -> s));
        Page<SourceFile> result = sourceFiles.findByLastScanRunIdAndParseStatusIn(
                scanRunId, List.of(ParseStatus.ERROR), PageRequest.of(page - 1, limit, Sort.by("relPath")));
        return new PageResponse<>(
                result.map(f -> toError(f, sources.get(f.getSourceId()))).getContent(),
                result.getTotalElements(),
                page,
                limit);
    }

    /** API B2 — luồng tiến độ; lần quét đã kết thúc thì gửi ngay sự kiện {@code done} rồi đóng. */
    public SseEmitter events(UUID scanRunId) {
        ScanRunResponse current = get(scanRunId);
        if (current.status().isFinished()) {
            SseEmitter emitter = new SseEmitter();
            try {
                emitter.send(SseEmitter.event().name(ProgressReporter.EVENT_DONE).data(current));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }
        return sseHub.subscribe(ProgressReporter.channel(scanRunId));
    }

    /** Lần quét PENDING/RUNNING còn trong CSDL khi Engine khởi động là do Engine dừng giữa chừng — đánh dấu thất bại. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedScans() {
        for (ScanRun run : scanRuns.findByStatusIn(EnumSet.of(ScanStatus.PENDING, ScanStatus.RUNNING))) {
            run.transitionTo(ScanStatus.FAILED, Instant.now());
            run.setErrorSummary("Engine dừng khi lần quét đang chạy; kết quả của lần quét trước được giữ nguyên");
            log.warn("Đánh dấu thất bại lần quét dở dang {}", run.getId());
        }
    }

    private ScanRun require(UUID scanRunId) {
        return scanRuns.findById(scanRunId)
                .orElseThrow(() -> new AppException(
                        ErrorCode.SCAN_NOT_FOUND, "Không tìm thấy lần quét", Map.of("scanRunId", scanRunId.toString())));
    }

    private static ScanFileErrorResponse toError(SourceFile f, SourceScanTarget source) {
        String root = source == null ? "" : source.rootPath();
        return new ScanFileErrorResponse(
                f.getSourceId(),
                source == null ? null : source.name(),
                f.getRelPath(),
                root.isEmpty() ? f.getRelPath() : root + "/" + f.getRelPath(),
                f.getParseError());
    }
}
