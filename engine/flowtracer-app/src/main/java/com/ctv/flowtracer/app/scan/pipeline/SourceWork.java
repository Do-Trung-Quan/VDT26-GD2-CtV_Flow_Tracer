package com.ctv.flowtracer.app.scan.pipeline;

import com.ctv.flowtracer.app.graph.model.SourceGraphWriteResult;
import com.ctv.flowtracer.app.scan.entity.SourceFile;
import com.ctv.flowtracer.app.workspace.model.SourceScanTarget;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Trạng thái quét của một Source, các bước lần lượt điền vào. Chỉ luồng quét của lần quét đó dùng. */
final class SourceWork {

    final SourceScanTarget target;
    /** Bộ phân tích; null nếu Source chưa nhận diện được hoặc adapter không còn được đăng ký. */
    final StackAdapter adapter;

    // DISCOVER
    /** Mọi file qua bộ lọc (kể cả file cấu hình), tương đối trong Source, đã sắp xếp. */
    List<String> allPaths = List.of();
    /** File bộ phân tích xử lý, theo đường dẫn. */
    final Map<String, DiscoveredFile> files = new TreeMap<>();

    // DIFF
    /** Bản ghi file của lần quét trước, theo đường dẫn. */
    final Map<String, SourceFile> previous = new TreeMap<>();
    final TreeSet<String> added = new TreeSet<>();
    final TreeSet<String> modified = new TreeSet<>();
    final TreeSet<String> removed = new TreeSet<>();
    final TreeSet<String> unchanged = new TreeSet<>();

    // EXTRACT
    final List<FileFacts> facts = new ArrayList<>();
    int extracted;
    int fromCache;
    /** File lỗi → lý do (lỗi đọc, lỗi bộ phân tích, quá thời gian; sau RESOLVE thêm lỗi cú pháp). */
    final Map<String, String> fileErrors = new TreeMap<>();

    // RESOLVE
    IrFragment fragment;

    // PERSIST
    SourceGraphWriteResult writeResult;

    /** Ghi chú khi Source không được phân tích. */
    String note;

    SourceWork(SourceScanTarget target, StackAdapter adapter) {
        this.target = target;
        this.adapter = adapter;
    }

    /** Source có bộ phân tích và có file để phân tích. */
    boolean analyzable() {
        return adapter != null && note == null;
    }

    /** Đường dẫn tương đối trong SOURCE_ROOT của một file trong Source. */
    String fullPath(String relativePath) {
        return target.rootPath().isEmpty() ? relativePath : target.rootPath() + "/" + relativePath;
    }
}
