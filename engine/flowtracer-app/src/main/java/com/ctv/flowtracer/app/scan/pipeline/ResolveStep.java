package com.ctv.flowtracer.app.scan.pipeline;

import com.ctv.flowtracer.app.scan.adapter.SidecarStackAdapter;
import com.ctv.flowtracer.app.scan.model.ScanStage;
import com.ctv.flowtracer.app.source.SourceReader;
import com.ctv.flowtracer.ir.Diagnostic;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Bước 4 — RESOLVE: chạy pha 2 của bộ phân tích cho từng Source (ghép dữ kiện → IR). Chẩn đoán mức lỗi gắn với một file
 * (vd lỗi cú pháp) làm file đó thành file lỗi.
 *
 * <p>TODO (Giai đoạn 5): truyền luật liên kết thủ công của Workspace vào {@code manualRules}.
 */
@Component
class ResolveStep {

    private final SourceReader reader;

    ResolveStep(SourceReader reader) {
        this.reader = reader;
    }

    void run(ScanContext ctx, ProgressReporter reporter) {
        long count = ctx.sources().stream().filter(SourceWork::analyzable).count();
        reporter.stage(ctx.scanRunId(), ScanStage.RESOLVE, (int) count, "Đang ghép kết quả phân tích");
        int done = 0;
        for (SourceWork work : ctx.sources()) {
            if (!work.analyzable()) {
                continue;
            }
            ctx.checkCancelled();
            // Adapter chạy trong sidecar cần biết Workspace (đặt cache) và nơi Source nằm trong SOURCE_ROOT
            Map<String, String> options = Map.of(
                    SidecarStackAdapter.OPTION_WORKSPACE_ID, ctx.workspaceId().toString(),
                    SidecarStackAdapter.OPTION_SOURCE_ROOT_PATH, work.target.rootPath());
            var resolveCtx = new ResolveContext(
                    work.target.sourceId().toString(), work.allPaths, ExtractStep.loaderFor(reader, work), null, options);
            work.fragment = work.adapter.resolve(resolveCtx, work.facts).sorted();
            markFileErrors(work, work.fragment);
            reporter.progress(ctx.scanRunId(), ScanStage.RESOLVE, ++done, (int) count,
                    work.target.name() + ": " + work.fragment.nodes().size() + " thành phần");
        }
    }

    private static void markFileErrors(SourceWork work, IrFragment fragment) {
        for (Diagnostic d : fragment.diagnostics()) {
            if (d.severity() == Diagnostic.Severity.ERROR && d.filePath() != null && work.files.containsKey(d.filePath())) {
                work.fileErrors.merge(d.filePath(), d.message(), (a, b) -> a + "; " + b);
            }
        }
    }
}
