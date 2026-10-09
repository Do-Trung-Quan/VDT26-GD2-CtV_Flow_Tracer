package com.ctv.flowtracer.app.scan.pipeline;

import com.ctv.flowtracer.app.config.FlowTracerProperties;
import com.ctv.flowtracer.app.scan.model.ScanStage;
import com.ctv.flowtracer.app.source.SourceReader;
import com.ctv.flowtracer.core.discovery.ContentHash;
import com.ctv.flowtracer.core.discovery.FileFilter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bước 1 — DISCOVER: liệt kê file theo luật lọc của từng Source (bỏ cả nhánh thư mục bị loại), đọc và tính mã băm các
 * file bộ phân tích xử lý. File quá lớn, nhị phân, sai bảng mã hoặc không đọc được được ghi nhận là file lỗi kèm lý do.
 */
@Component
class DiscoverStep {

    private static final Logger log = LoggerFactory.getLogger(DiscoverStep.class);

    private static final int BINARY_PROBE_BYTES = 8 * 1024;

    private final SourceReader reader;
    private final long maxFileBytes;

    DiscoverStep(SourceReader reader, FlowTracerProperties properties) {
        this.reader = reader;
        this.maxFileBytes = properties.scan().maxFileSizeKb() * 1024L;
    }

    void run(ScanContext ctx, ProgressReporter reporter) {
        reporter.stage(ctx.scanRunId(), ScanStage.DISCOVER, ctx.sources().size(), "Đang liệt kê file");
        int done = 0;
        for (SourceWork work : ctx.sources()) {
            ctx.checkCancelled();
            if (work.adapter != null) {
                discover(ctx, work);
            }
            reporter.progress(ctx.scanRunId(), ScanStage.DISCOVER, ++done, ctx.sources().size(),
                    "Đã liệt kê " + work.files.size() + " file của " + work.target.name());
        }
    }

    private void discover(ScanContext ctx, SourceWork work) {
        long start = System.nanoTime();
        var filter = new FileFilter(work.target.includeGlobs(), work.target.excludeGlobs(), Long.MAX_VALUE);
        List<SourceReader.Entry> entries =
                reader.walkFiles(work.target.rootPath(), filter::acceptDirectory, filter::acceptInWalkedDirectory);
        long walked = System.nanoTime();
        int prefix = work.target.rootPath().isEmpty() ? 0 : work.target.rootPath().length() + 1;
        work.allPaths = entries.stream().map(e -> e.relativePath().substring(prefix)).sorted().toList();
        for (SourceReader.Entry entry : entries) {
            String relative = entry.relativePath().substring(prefix);
            if (work.adapter.analyzes(relative)) {
                ctx.checkCancelled();
                work.files.put(relative, read(entry.relativePath(), relative, entry.sizeBytes()));
            }
        }
        log.debug("DISCOVER {}: duyệt {} file trong {} ms, đọc {} file trong {} ms", work.target.name(), entries.size(),
                (walked - start) / 1_000_000, work.files.size(), (System.nanoTime() - walked) / 1_000_000);
        if (work.files.isEmpty()) {
            work.note = work.allPaths.isEmpty()
                    ? "Thư mục không có file nào sau khi áp luật lọc"
                    : "Bộ phân tích '" + work.adapter.id() + "' không xử lý file nào của nguồn này";
        }
    }

    private DiscoveredFile read(String fullPath, String relative, long size) {
        byte[] bytes;
        try {
            bytes = reader.readBytes(fullPath);
        } catch (UncheckedIOException e) {
            // Không có nội dung để băm: dùng mã băm của nội dung rỗng; file vẫn được báo là file lỗi
            return new DiscoveredFile(relative, size, ContentHash.sha256(new byte[0]), null, "Không đọc được file");
        }
        String hash = ContentHash.sha256(bytes);
        if (bytes.length > maxFileBytes) {
            return new DiscoveredFile(relative, bytes.length, hash, null,
                    "File lớn hơn giới hạn " + (maxFileBytes / 1024) + " KB");
        }
        if (FileFilter.looksBinary(Arrays.copyOf(bytes, Math.min(bytes.length, BINARY_PROBE_BYTES)))) {
            return new DiscoveredFile(relative, bytes.length, hash, null, "File nhị phân, không phải mã nguồn văn bản");
        }
        try {
            String content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return new DiscoveredFile(relative, bytes.length, hash, stripBom(content), null);
        } catch (CharacterCodingException e) {
            return new DiscoveredFile(relative, bytes.length, hash, null, "Không đọc được theo bảng mã UTF-8");
        }
    }

    private static String stripBom(String content) {
        return content.startsWith("\uFEFF") ? content.substring(1) : content;
    }
}
