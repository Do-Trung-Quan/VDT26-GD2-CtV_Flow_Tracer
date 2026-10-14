package com.ctv.flowtracer.app.scan.pipeline;

import com.ctv.flowtracer.app.graph.service.GraphWriteService;
import com.ctv.flowtracer.app.scan.entity.ParseStatus;
import com.ctv.flowtracer.app.scan.entity.SourceFile;
import com.ctv.flowtracer.app.scan.model.ScanStage;
import com.ctv.flowtracer.app.scan.repository.SourceFileRepository;
import com.ctv.flowtracer.ir.IrFragment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bước 5 — PERSIST: ghi danh sách file (mã băm, trạng thái phân tích) và IR của mọi Source trong MỘT transaction. Lỗi
 * giữa chừng thì không có gì được ghi — IR của lần quét thành công trước được giữ nguyên.
 */
@Component
class PersistStep {

    private static final int LANGUAGE_MAX_LENGTH = 20;

    private final TransactionTemplate tx;
    private final SourceFileRepository sourceFiles;
    private final GraphWriteService graphWriter;

    PersistStep(TransactionTemplate tx, SourceFileRepository sourceFiles, GraphWriteService graphWriter) {
        this.tx = tx;
        this.sourceFiles = sourceFiles;
        this.graphWriter = graphWriter;
    }

    void run(ScanContext ctx, ProgressReporter reporter) {
        ctx.checkCancelled(); // điểm hủy cuối cùng — sau đây lần quét sẽ ghi trọn vẹn
        reporter.stage(ctx.scanRunId(), ScanStage.PERSIST, ctx.sources().size(), "Đang lưu kết quả");
        tx.executeWithoutResult(status -> {
            int done = 0;
            for (SourceWork work : ctx.sources()) {
                Map<String, UUID> fileIds = saveFiles(ctx.scanRunId(), work);
                IrFragment fragment = work.fragment != null
                        ? work.fragment
                        : IrFragment.empty(work.adapter == null ? "unknown" : work.adapter.id());
                work.writeResult = graphWriter.replaceSourceGraph(ctx.workspaceId(), work.target.sourceId(), fragment, fileIds);
                reporter.progress(ctx.scanRunId(), ScanStage.PERSIST, ++done, ctx.sources().size(), "Đã lưu " + work.target.name());
            }
        });
    }

    /** Thêm/cập nhật bản ghi file hiện có, xóa bản ghi file không còn; trả đường dẫn → id. */
    private Map<String, UUID> saveFiles(UUID scanRunId, SourceWork work) {
        var toDelete = new ArrayList<SourceFile>();
        work.previous.forEach((path, f) -> {
            if (!work.files.containsKey(path)) {
                toDelete.add(f);
            }
        });
        work.previousConfig.forEach((path, f) -> {
            if (!work.configFiles.containsKey(path)) {
                toDelete.add(f);
            }
        });
        sourceFiles.deleteAll(toDelete);

        var toSave = new ArrayList<SourceFile>();
        var ids = new TreeMap<String, UUID>();
        for (DiscoveredFile file : work.files.values()) {
            SourceFile entity = work.previous.get(file.relativePath());
            // File không đổi, từng phân tích tốt và lần này cũng không lỗi: bản ghi giữ nguyên (chỉ cần đánh dấu lần quét — cập nhật hàng loạt
            // ở cuối); khỏi đọc + ghi lại từng bản ghi, việc này tốn hàng giây với hàng nghìn file
            if (entity != null && work.unchanged.contains(file.relativePath()) && entity.getParseStatus() == ParseStatus.OK
                    && !work.fileErrors.containsKey(file.relativePath())) {
                ids.put(entity.getRelPath(), entity.getId());
                continue;
            }
            if (entity == null) {
                entity = new SourceFile(
                        work.target.sourceId(), file.relativePath(), file.contentHash(), language(file.relativePath()),
                        (int) Math.min(Integer.MAX_VALUE, file.sizeBytes()), scanRunId);
            } else {
                entity.setContentHash(file.contentHash());
                entity.setSizeBytes((int) Math.min(Integer.MAX_VALUE, file.sizeBytes()));
                entity.setLastScanRunId(scanRunId);
            }
            String error = work.fileErrors.get(file.relativePath());
            entity.markParsed(error == null ? ParseStatus.OK : ParseStatus.ERROR, error);
            toSave.add(entity);
        }
        for (DiscoveredFile file : work.configFiles.values()) {
            SourceFile entity = work.previousConfig.get(file.relativePath());
            if (entity != null && !work.configChanged.contains(file.relativePath())) {
                ids.put(entity.getRelPath(), entity.getId());
                continue;
            }
            if (entity == null) {
                entity = new SourceFile(
                        work.target.sourceId(), file.relativePath(), file.contentHash(), SourceFile.LANGUAGE_CONFIG,
                        (int) Math.min(Integer.MAX_VALUE, file.sizeBytes()), scanRunId);
            } else {
                entity.setContentHash(file.contentHash());
                entity.setSizeBytes((int) Math.min(Integer.MAX_VALUE, file.sizeBytes()));
                entity.setLastScanRunId(scanRunId);
            }
            toSave.add(entity);
        }
        sourceFiles.saveAllAndFlush(toSave).forEach(f -> ids.put(f.getRelPath(), f.getId()));
        sourceFiles.markScanned(List.of(work.target.sourceId()), scanRunId);
        return ids;
    }

    /** Ngôn ngữ theo đuôi file (vd {@code java}). */
    static String language(String relativePath) {
        String name = relativePath.substring(relativePath.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "other" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.length() > LANGUAGE_MAX_LENGTH ? ext.substring(0, LANGUAGE_MAX_LENGTH) : ext;
    }
}
