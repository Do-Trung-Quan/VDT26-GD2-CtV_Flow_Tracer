package com.ctv.flowtracer.app.scan.pipeline;

import com.ctv.flowtracer.app.scan.entity.SourceFile;
import com.ctv.flowtracer.app.scan.model.ScanStage;
import com.ctv.flowtracer.app.scan.repository.SourceFileRepository;
import org.springframework.stereotype.Component;

/** Bước 2 — DIFF: so mã băm từng file với bản ghi của lần quét trước để biết file thêm / sửa / xóa / giữ nguyên. */
@Component
class DiffStep {

    private final SourceFileRepository sourceFiles;

    DiffStep(SourceFileRepository sourceFiles) {
        this.sourceFiles = sourceFiles;
    }

    void run(ScanContext ctx, ProgressReporter reporter) {
        reporter.stage(ctx.scanRunId(), ScanStage.DIFF, ctx.sources().size(), "Đang so sánh với lần quét trước");
        int done = 0;
        for (SourceWork work : ctx.sources()) {
            ctx.checkCancelled();
            diff(work);
            reporter.progress(ctx.scanRunId(), ScanStage.DIFF, ++done, ctx.sources().size(),
                    work.target.name() + ": +" + work.added.size() + " / ~" + work.modified.size() + " / -" + work.removed.size());
        }
    }

    private void diff(SourceWork work) {
        for (SourceFile previous : sourceFiles.findBySourceIdOrderByRelPath(work.target.sourceId())) {
            work.previous.put(previous.getRelPath(), previous);
        }
        work.files.forEach((path, file) -> {
            SourceFile previous = work.previous.get(path);
            if (previous == null) {
                work.added.add(path);
            } else if (previous.getContentHash().equals(file.contentHash())) {
                work.unchanged.add(path);
            } else {
                work.modified.add(path);
            }
        });
        work.previous.keySet().stream().filter(p -> !work.files.containsKey(p)).forEach(work.removed::add);
    }
}
