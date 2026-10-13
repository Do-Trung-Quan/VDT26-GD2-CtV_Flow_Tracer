package com.ctv.flowtracer.app.scan.pipeline;

import com.ctv.flowtracer.app.config.FlowTracerProperties;
import com.ctv.flowtracer.app.scan.entity.ScanTrigger;
import com.ctv.flowtracer.app.scan.model.ScanStage;
import com.ctv.flowtracer.app.scan.service.FactsCache;
import com.ctv.flowtracer.app.source.SourceReader;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.SourceContentLoader;
import com.ctv.flowtracer.ir.spi.SourceFile;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Bước 3 — EXTRACT: chạy pha 1 của bộ phân tích cho từng file, song song trên virtual threads (tối đa
 * {@code flowtracer.scan.workers} file cùng lúc).
 *
 * <ul>
 *   <li>{@code IMPORT}: đọc lại mọi file (vẫn ghi cache).
 *   <li>{@code RESCAN}: chỉ đọc file thêm/sửa; file không đổi lấy dữ kiện từ cache (thiếu cache thì đọc lại).
 * </ul>
 *
 * Lỗi của bộ phân tích ở một file, hoặc quá thời gian, chỉ làm file đó thành file lỗi.
 */
@Component
class ExtractStep {

    private static final Logger log = LoggerFactory.getLogger(ExtractStep.class);
    static final Duration FILE_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final FactsCache factsCache;
    private final SourceReader reader;
    private final ExecutorService executor;
    private final int workers;

    ExtractStep(
            FactsCache factsCache,
            SourceReader reader,
            @Qualifier("scanExecutor") ExecutorService executor,
            FlowTracerProperties properties) {
        this.factsCache = factsCache;
        this.reader = reader;
        this.executor = executor;
        int configured = properties.scan().workers();
        this.workers = configured > 0 ? configured : Runtime.getRuntime().availableProcessors();
    }

    void run(ScanContext ctx, ProgressReporter reporter) {
        int total = ctx.sources().stream().filter(SourceWork::analyzable).mapToInt(w -> w.files.size()).sum();
        reporter.stage(ctx.scanRunId(), ScanStage.EXTRACT, total, "Đang đọc và trích xuất file");
        int[] done = {0};
        for (SourceWork work : ctx.sources()) {
            if (work.analyzable()) {
                extract(ctx, work, () -> reporter.progress(
                        ctx.scanRunId(), ScanStage.EXTRACT, ++done[0], total, "Đã xử lý " + done[0] + "/" + total + " file"));
            }
        }
    }

    private void extract(ScanContext ctx, SourceWork work, Runnable onFileDone) {
        var extractCtx = new ExtractContext(work.target.sourceId().toString(), work.allPaths, loaderFor(reader, work), Map.of());
        var semaphore = new Semaphore(workers);
        Map<String, Future<FileFacts>> pending = new LinkedHashMap<>();
        for (DiscoveredFile file : work.files.values()) {
            ctx.checkCancelled();
            if (file.readError() != null) {
                work.fileErrors.put(file.relativePath(), file.readError());
                onFileDone.run();
                continue;
            }
            Optional<FileFacts> cached = ctx.trigger() == ScanTrigger.RESCAN && work.unchanged.contains(file.relativePath())
                    ? factsCache.get(ctx.workspaceId(), work.adapter, file.contentHash())
                    : Optional.empty();
            if (cached.isPresent()) {
                work.facts.add(cached.get());
                work.fromCache++;
                onFileDone.run();
                continue;
            }
            pending.put(file.relativePath(), executor.submit(() -> {
                semaphore.acquire();
                try {
                    FileFacts facts = work.adapter.extract(
                            extractCtx, new SourceFile(file.relativePath(), file.content(), file.contentHash()));
                    factsCache.put(ctx.workspaceId(), work.adapter, file.contentHash(), facts);
                    return facts;
                } finally {
                    semaphore.release();
                }
            }));
        }
        collect(ctx, work, pending, onFileDone);
        extractLibraries(ctx, work, extractCtx);
    }

    /** Lớp thư viện rút gọn: nhỏ, nhanh nên chạy tuần tự; hỏng một lớp chỉ mất lớp đó (kiểu hiện là "object"). */
    private void extractLibraries(ScanContext ctx, SourceWork work, ExtractContext extractCtx) {
        for (SourceFile stub : work.libraryFiles.values()) {
            ctx.checkCancelled();
            try {
                Optional<FileFacts> cached = factsCache.get(ctx.workspaceId(), work.adapter, stub.contentHash());
                if (cached.isPresent()) {
                    work.facts.add(cached.get());
                    continue;
                }
                FileFacts facts = work.adapter.extract(extractCtx, stub);
                factsCache.put(ctx.workspaceId(), work.adapter, stub.contentHash(), facts);
                work.facts.add(facts);
            } catch (RuntimeException e) {
                log.debug("Bỏ qua lớp thư viện {}: {}", stub.relativePath(), e.toString());
            }
        }
    }

    private void collect(ScanContext ctx, SourceWork work, Map<String, Future<FileFacts>> pending, Runnable onFileDone) {
        try {
            for (var entry : pending.entrySet()) {
                String path = entry.getKey();
                try {
                    work.facts.add(await(ctx, entry.getValue()));
                    work.extracted++;
                } catch (TimeoutException e) {
                    entry.getValue().cancel(true);
                    work.fileErrors.put(path, "Quá thời gian phân tích (" + FILE_TIMEOUT.toSeconds() + " giây)");
                } catch (ExecutionException e) {
                    log.warn("Bộ phân tích '{}' lỗi ở file {}", work.adapter.id(), path, e.getCause());
                    work.fileErrors.put(path, "Bộ phân tích gặp lỗi ở file này: " + e.getCause().getClass().getSimpleName());
                }
                onFileDone.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScanContext.ScanCancelledException();
        } finally {
            pending.values().forEach(f -> f.cancel(true));
        }
    }

    /** Chờ một file theo từng nhịp ngắn để yêu cầu hủy được đáp ứng ngay, tối đa {@link #FILE_TIMEOUT}. */
    private static FileFacts await(ScanContext ctx, Future<FileFacts> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        long deadline = System.nanoTime() + FILE_TIMEOUT.toNanos();
        while (true) {
            ctx.checkCancelled();
            try {
                return future.get(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                if (System.nanoTime() >= deadline) {
                    throw e;
                }
            }
        }
    }

    /** Bộ phân tích đọc file khác của Source (vd file cấu hình) qua SourceReader — không tự đọc file. */
    static SourceContentLoader loaderFor(SourceReader reader, SourceWork work) {
        return relativePath -> {
            String full = work.fullPath(relativePath);
            try {
                return reader.exists(full) ? Optional.of(reader.readString(full)) : Optional.empty();
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        };
    }
}
