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

    /**
     * "Mã nguồn rút gọn" của các lớp thư viện (jar) mà mã dự án dùng, theo đường dẫn giả {@code libs/x.jar!/com/acme/Foo.java}.
     * Chỉ để phân tích kiểu (vd lớp bọc kết quả trả về): không phải file của dự án nên không vào danh sách file, không tính vào
     * số liệu quét và không ghi vào CSDL.
     */
    final Map<String, com.ctv.flowtracer.ir.spi.SourceFile> libraryFiles = new TreeMap<>();

    /** File cấu hình ảnh hưởng tới kết quả (không phân tích), đã băm — {@code content} luôn null. */
    final Map<String, DiscoveredFile> configFiles = new TreeMap<>();

    // DIFF
    /** Bản ghi file ĐƯỢC PHÂN TÍCH của lần quét trước, theo đường dẫn. */
    final Map<String, SourceFile> previous = new TreeMap<>();
    /** Bản ghi file cấu hình của lần quét trước. */
    final Map<String, SourceFile> previousConfig = new TreeMap<>();
    /** File cấu hình thêm / sửa / xóa so với lần quét trước. */
    final TreeSet<String> configChanged = new TreeSet<>();
    /** Bộ phân tích đã đổi phiên bản so với lần quét thành công trước: phải phân tích lại dù không file nào đổi. */
    boolean analysisChanged;
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
