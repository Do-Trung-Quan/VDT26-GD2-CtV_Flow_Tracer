package com.ctv.flowtracer.ir.spi;

import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.NoiseFilter;
import com.ctv.flowtracer.ir.SourceRole;
import java.util.List;
import java.util.Set;

/**
 * Hợp đồng chung của mọi bộ phân tích theo công nghệ (xem §2.1.4 của overview). Lõi phân tích không chứa logic
 * riêng của công nghệ nào; mọi thứ riêng nằm trong adapter. Adapter chạy trong sidecar được ứng dụng bọc bằng
 * {@code SidecarStackAdapter}.
 */
public interface StackAdapter {

    /** Định danh ổn định, vd "spring", "angular", "php-legacy". */
    String id();

    /** Vai trò Source mà adapter hỗ trợ. */
    Set<SourceRole> roles();

    /**
     * Adapter có phân tích file này không (vd adapter Spring chỉ đọc {@code .java}). Chỉ file được phân tích mới qua pha
     * 1 và được ghi vào danh sách file của lần quét; các file khác vẫn đọc được qua loader (file cấu hình).
     *
     * @param relativePath đường dẫn tương đối trong Source, dùng dấu '/'
     */
    default boolean analyzes(String relativePath) {
        return true;
    }

    /**
     * File KHÔNG được phân tích nhưng ảnh hưởng tới kết quả (vd {@code application.yml} quyết định context-path và đường
     * dẫn API). Tool băm các file này; một trong số chúng đổi thì Quét lại phải ghép kết quả lại dù không có file mã nguồn
     * nào đổi.
     *
     * @param relativePath đường dẫn tương đối trong Source, dùng dấu '/'
     */
    default boolean affectsAnalysis(String relativePath) {
        return false;
    }

    /**
     * File này có phải thư viện đóng gói sẵn mà adapter muốn đọc khai báo kiểu bên trong (vd {@code .jar} chứa lớp bọc
     * {@code ApiResponse<T>} của dự án) không. Chỉ file thỏa điều kiện này được đọc nguyên bytes và đưa vào
     * {@link #libraryFiles(List, List)}.
     *
     * @param relativePath đường dẫn tương đối trong Source, dùng dấu '/'
     */
    default boolean isLibraryArchive(String relativePath) {
        return false;
    }

    /**
     * Dựng "mã nguồn rút gọn" (chỉ khai báo kiểu và trường) cho những lớp thư viện mà mã dự án thật sự dùng, để các lớp đó
     * được phân tích như mã nguồn thường — vd lớp bọc kết quả trả về nằm trong jar. Chạy một lần cho cả Source, sau khi
     * đã đọc xong file mã nguồn.
     *
     * @param archives các thư viện thỏa {@link #isLibraryArchive(String)}
     * @param projectFiles file mã nguồn của dự án (để biết lớp nào được dùng, lớp nào đã có mã nguồn thật)
     * @return file rút gọn, đường dẫn dạng {@code libs/x.jar!/com/acme/Foo.java}; sắp xếp ổn định
     */
    default List<SourceFile> libraryFiles(List<LibraryArchive> archives, List<SourceFile> projectFiles) {
        return List.of();
    }

    /** Phiên bản cách trích dữ kiện pha 1 — đổi thì dữ kiện đã cache với phiên bản cũ không được dùng lại. */
    default String factsVersion() {
        return "1";
    }

    /** Kiểu dữ kiện pha 1 của adapter — dùng để đọc lại từ cache. */
    Class<? extends FileFacts> factsType();

    /**
     * Tên file đặc trưng ở gốc dự án (vd {@code pom.xml}, {@code angular.json}). Thư mục chứa một trong các file này là
     * ứng viên gốc dự án và được đưa vào {@link #detect(DetectContext)}.
     */
    default List<String> rootMarkers() {
        return List.of();
    }

    /** Mức độ phù hợp (0..1) dựa trên các file đặc trưng của Source. */
    DetectResult detect(DetectContext ctx);

    /**
     * Pha 1 — từng file độc lập, chạy song song: trích dữ kiện thô. Kết quả được cache theo mã băm file. Không
     * được ném lỗi vì file sai cú pháp; ghi lỗi vào dữ kiện để pha 2 sinh {@code Diagnostic}.
     */
    FileFacts extract(ExtractContext ctx, SourceFile file);

    /** Pha 2 — một lần cho cả Source: phân giải tham chiếu chéo và sinh IR hoàn chỉnh, đã sắp xếp ổn định. */
    IrFragment resolve(ResolveContext ctx, List<FileFacts> facts);

    /** Bộ lọc nhiễu mặc định của công nghệ. */
    default List<NoiseFilter> defaultNoiseFilters() {
        return List.of();
    }
}
