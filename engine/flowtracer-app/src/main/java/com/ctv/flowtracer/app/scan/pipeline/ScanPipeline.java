package com.ctv.flowtracer.app.scan.pipeline;

import com.ctv.flowtracer.app.graph.service.GraphQueryService;
import com.ctv.flowtracer.app.scan.dto.response.ScanRunResponse;
import com.ctv.flowtracer.app.scan.entity.ParseStatus;
import com.ctv.flowtracer.app.scan.entity.ScanRun;
import com.ctv.flowtracer.app.scan.entity.ScanStatus;
import com.ctv.flowtracer.app.scan.entity.ScanTrigger;
import com.ctv.flowtracer.app.scan.model.FileCounts;
import com.ctv.flowtracer.app.scan.model.ScanDiagnostic;
import com.ctv.flowtracer.app.scan.model.ScanHandle;
import com.ctv.flowtracer.app.scan.model.ScanStage;
import com.ctv.flowtracer.app.scan.model.ScanStats;
import com.ctv.flowtracer.app.scan.model.SourceScanStats;
import com.ctv.flowtracer.app.scan.model.StageDuration;
import com.ctv.flowtracer.app.scan.repository.ScanRunRepository;
import com.ctv.flowtracer.app.scan.repository.SourceFileRepository;
import com.ctv.flowtracer.app.scan.service.ScanQueryService;
import com.ctv.flowtracer.app.workspace.model.SourceScanTarget;
import com.ctv.flowtracer.ir.Diagnostic;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Người điều phối một lần quét: DISCOVER → DIFF → EXTRACT → RESOLVE → PERSIST → FINALIZE. Đo thời gian thật từng giai
 * đoạn, dừng khi bị hủy, ghi trạng thái cuối và báo {@code done} qua SSE.
 *
 * <ul>
 *   <li>Hủy hoặc lỗi trước/trong PERSIST: IR và danh sách file của lần quét trước được giữ nguyên.
 *   <li>Có file lỗi: {@code PARTIAL}; các phần khác vẫn được lưu.
 *   <li>RESCAN không có file nào đổi: bỏ qua EXTRACT/RESOLVE/PERSIST, {@code noChanges = true}.
 * </ul>
 *
 * <p>TODO (Giai đoạn 5): bước LINK nối Frontend ↔ Backend sau PERSIST.
 */
@Component
public class ScanPipeline {

    private static final Logger log = LoggerFactory.getLogger(ScanPipeline.class);
    private static final int ERROR_SUMMARY_MAX = 500;

    private final DiscoverStep discover;
    private final DiffStep diff;
    private final ExtractStep extract;
    private final ResolveStep resolve;
    private final PersistStep persist;
    private final ProgressReporter reporter;
    private final ScanRunRepository scanRuns;
    private final SourceFileRepository sourceFiles;
    private final ScanQueryService scanQueries;
    private final GraphQueryService graphQueries;

    ScanPipeline(
            DiscoverStep discover,
            DiffStep diff,
            ExtractStep extract,
            ResolveStep resolve,
            PersistStep persist,
            ProgressReporter reporter,
            ScanRunRepository scanRuns,
            SourceFileRepository sourceFiles,
            ScanQueryService scanQueries,
            GraphQueryService graphQueries) {
        this.discover = discover;
        this.diff = diff;
        this.extract = extract;
        this.resolve = resolve;
        this.persist = persist;
        this.reporter = reporter;
        this.scanRuns = scanRuns;
        this.sourceFiles = sourceFiles;
        this.scanQueries = scanQueries;
        this.graphQueries = graphQueries;
    }

    /**
     * Chạy trọn một lần quét (trên luồng nền). Không ném lỗi ra ngoài: mọi kết cục đều được ghi vào {@code scan_runs}.
     * Người gọi mở khóa Workspace rồi mới báo {@code done} cho giao diện.
     *
     * @param adapters id adapter → adapter
     * @return trạng thái cuối của lần quét
     */
    public ScanRunResponse run(
            ScanHandle handle, ScanTrigger trigger, List<SourceScanTarget> targets, Function<String, StackAdapter> adapters) {
        var ctx = new ScanContext(handle, trigger, targets.stream()
                .map(t -> new SourceWork(t, t.adapterId() == null ? null : adapters.apply(t.adapterId())))
                .toList());
        ctx.sources().stream()
                .filter(w -> w.adapter == null)
                .forEach(w -> w.note = "Chưa xác định được bộ phân tích cho nguồn này");
        try {
            transition(ctx, ScanStatus.RUNNING, null, null);
            timed(ctx, ScanStage.DISCOVER, () -> discover.run(ctx, reporter));
            timed(ctx, ScanStage.DIFF, () -> diff.run(ctx, reporter));
            markAnalysisChanges(ctx);
            boolean noChanges = trigger == ScanTrigger.RESCAN && !hasChanges(ctx);
            if (noChanges) {
                ctx.checkCancelled();
                sourceFiles.markScanned(ctx.sources().stream().map(w -> w.target.sourceId()).toList(), ctx.scanRunId());
            } else {
                timed(ctx, ScanStage.EXTRACT, () -> extract.run(ctx, reporter));
                timed(ctx, ScanStage.RESOLVE, () -> resolve.run(ctx, reporter));
                timed(ctx, ScanStage.PERSIST, () -> persist.run(ctx, reporter));
            }
            long start = System.nanoTime();
            reporter.stage(ctx.scanRunId(), ScanStage.FINALIZE, 0, "Đang tính thống kê");
            ScanStats stats = stats(ctx, noChanges);
            ctx.stageDurationsMs().put(ScanStage.FINALIZE, (System.nanoTime() - start) / 1_000_000);
            stats = withDurations(stats, ctx);
            ScanStatus status = stats.files().errors() > 0 ? ScanStatus.PARTIAL : ScanStatus.SUCCEEDED;
            String note = noChanges
                    ? "Không có thay đổi kể từ lần quét trước"
                    : ctx.sources().stream().anyMatch(w -> w.analysisChanged)
                            ? "Bộ phân tích đã được nâng cấp kể từ lần quét trước nên phân tích lại toàn bộ"
                            : null;
            return finish(ctx, status, note, stats);
        } catch (ScanContext.ScanCancelledException e) {
            return finish(ctx, ScanStatus.CANCELLED, "Đã hủy; kết quả của lần quét trước được giữ nguyên", null);
        } catch (RuntimeException e) {
            log.error("Lần quét {} thất bại", ctx.scanRunId(), e);
            return finish(ctx, ScanStatus.FAILED, summarize(e), null);
        }
    }

    private void timed(ScanContext ctx, ScanStage stage, Runnable step) {
        long start = System.nanoTime();
        step.run();
        ctx.stageDurationsMs().put(stage, (System.nanoTime() - start) / 1_000_000);
    }

    private static boolean hasChanges(ScanContext ctx) {
        return ctx.sources().stream()
                .anyMatch(w -> !w.added.isEmpty() || !w.modified.isEmpty() || !w.removed.isEmpty() || !w.configChanged.isEmpty()
                        || w.analysisChanged);
    }

    /** Phiên bản bộ phân tích đang dùng, ghi vào thống kê của lần quét để lần sau so sánh. */
    private static String analysisVersion(SourceWork w) {
        return w.adapter == null ? null : w.adapter.id() + "-v" + w.adapter.factsVersion();
    }

    /**
     * IR của lần quét thành công trước được dựng bởi một phiên bản bộ phân tích; phiên bản đã đổi (hoặc lần quét đó ghi
     * trước khi có thông tin này) thì IR cũ thiếu những gì bộ phân tích mới biết, nên phải phân tích lại dù không file nào
     * đổi — nếu không "Quét lại" sẽ báo "không có thay đổi" và giữ IR cũ mãi.
     */
    private void markAnalysisChanges(ScanContext ctx) {
        ScanStats previous = scanRuns.findFirstByWorkspaceIdAndStatusInAndIdNotOrderByStartedAtDesc(
                        ctx.workspaceId(), EnumSet.of(ScanStatus.SUCCEEDED, ScanStatus.PARTIAL), ctx.scanRunId())
                .map(scanQueries::statsOf)
                .orElse(null);
        if (previous == null) {
            return;
        }
        for (SourceWork w : ctx.sources()) {
            if (!w.analyzable()) {
                continue;
            }
            // analysisVersion của lần quét cũ có thể null (ghi trước khi có trường này) → coi như đã đổi
            SourceScanStats before = previous.sources().stream()
                    .filter(s -> w.target.sourceId().equals(s.sourceId()))
                    .findFirst()
                    .orElse(null);
            w.analysisChanged = before == null || !analysisVersion(w).equals(before.analysisVersion());
        }
    }

    /** Thống kê từ số liệu thật: số file đếm trong lần quét, số thành phần đọc lại từ CSDL sau khi lưu. */
    private ScanStats stats(ScanContext ctx, boolean noChanges) {
        var sources = ctx.sources().stream().map(w -> sourceStats(w, noChanges)).toList();
        FileCounts files = sources.stream().map(SourceScanStats::files).reduce(FileCounts.empty(), FileCounts::plus);
        if (noChanges) {
            // Không phân tích lại: IR vẫn là kết quả của lần quét trước, nên chẩn đoán cũng lấy từ lần đó
            ScanStats previous = scanRuns.findFirstByWorkspaceIdAndStatusInAndIdNotOrderByStartedAtDesc(
                            ctx.workspaceId(), EnumSet.of(ScanStatus.SUCCEEDED, ScanStatus.PARTIAL), ctx.scanRunId())
                    .map(scanQueries::statsOf)
                    .orElse(null);
            return new ScanStats(
                    files,
                    graphQueries.countNodes(ctx.workspaceId()),
                    graphQueries.countEdges(ctx.workspaceId()),
                    graphQueries.countNodes(ctx.workspaceId(), NodeKind.API_ENDPOINT),
                    previous == null ? 0 : previous.diagnosticErrors(),
                    previous == null ? 0 : previous.diagnosticWarnings(),
                    true,
                    0,
                    List.of(),
                    previous == null ? List.of() : previous.diagnostics(),
                    sources);
        }
        var diagnostics = new ArrayList<ScanDiagnostic>();
        for (SourceWork w : ctx.sources()) {
            if (w.fragment != null) {
                w.fragment.diagnostics().forEach(d -> diagnostics.add(
                        new ScanDiagnostic(d.severity(), w.target.sourceId(), d.filePath(), d.line(), d.message())));
            }
        }
        int errors = (int) diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).count();
        int warnings = (int) diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.WARNING).count();
        diagnostics.sort(Comparator.comparing(ScanDiagnostic::severity, Comparator.reverseOrder())); // ERROR trước
        return new ScanStats(
                files,
                graphQueries.countNodes(ctx.workspaceId()),
                graphQueries.countEdges(ctx.workspaceId()),
                graphQueries.countNodes(ctx.workspaceId(), NodeKind.API_ENDPOINT),
                errors,
                warnings,
                noChanges,
                ctx.sources().stream().mapToInt(w -> w.configChanged.size()).sum(),
                List.of(),
                diagnostics.stream().limit(ScanStats.MAX_DIAGNOSTICS).toList(),
                sources);
    }

    private SourceScanStats sourceStats(SourceWork w, boolean noChanges) {
        int fileErrors = noChanges
                ? (int) w.previous.values().stream().filter(f -> f.getParseStatus() == ParseStatus.ERROR).count()
                : w.fileErrors.size();
        var files = new FileCounts(
                w.files.size(),
                w.added.size(),
                w.modified.size(),
                w.removed.size(),
                w.unchanged.size(),
                w.extracted,
                w.fromCache,
                fileErrors);
        int skipped = w.writeResult == null ? 0 : w.writeResult.skippedKeys().size();
        return new SourceScanStats(
                w.target.sourceId(),
                w.target.name(),
                w.adapter == null ? null : w.adapter.id(),
                files,
                (int) graphQueries.countNodesOfSource(w.target.sourceId()),
                (int) graphQueries.countNodesOfSource(w.target.sourceId(), NodeKind.API_ENDPOINT),
                skipped,
                w.note,
                analysisVersion(w));
    }

    private static ScanStats withDurations(ScanStats s, ScanContext ctx) {
        List<StageDuration> durations = ctx.stageDurationsMs().entrySet().stream()
                .map(e -> new StageDuration(e.getKey(), e.getValue()))
                .toList();
        return new ScanStats(s.files(), s.nodes(), s.edges(), s.endpoints(), s.diagnosticErrors(), s.diagnosticWarnings(),
                s.noChanges(), s.configChanges(), durations, s.diagnostics(), s.sources());
    }

    private void transition(ScanContext ctx, ScanStatus status, String note, ScanStats stats) {
        ScanRun run = scanRuns.findById(ctx.scanRunId()).orElseThrow();
        run.transitionTo(status, Instant.now());
        run.setErrorSummary(note);
        if (stats != null) {
            run.setStats(scanQueries.toMap(stats));
        }
        scanRuns.save(run);
    }

    /** Ghi trạng thái cuối; null nếu chính việc ghi thất bại (CSDL mất kết nối…). */
    private ScanRunResponse finish(ScanContext ctx, ScanStatus status, String note, ScanStats stats) {
        try {
            transition(ctx, status, note, stats);
            ScanRun run = scanRuns.findById(ctx.scanRunId()).orElseThrow();
            return ScanRunResponse.from(run, scanQueries.statsOf(run), false);
        } catch (RuntimeException e) {
            log.error("Không ghi được trạng thái cuối của lần quét {}", ctx.scanRunId(), e);
            return null;
        }
    }

    private static String summarize(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String text = "Lỗi khi quét: " + message;
        return text.length() > ERROR_SUMMARY_MAX ? text.substring(0, ERROR_SUMMARY_MAX) + "…" : text;
    }
}
