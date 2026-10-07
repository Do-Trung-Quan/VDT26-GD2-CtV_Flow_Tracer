/**
 * Chức năng nhóm 2 (API B): quét và phân tích mã nguồn theo pipeline, quản lý lần quét, gọi sidecar, cache dữ kiện. Bảng: scan_runs, source_files.
 *
 * <p>Cấu trúc chuẩn mỗi chức năng: {@code controller} → {@code service} → {@code repository}, cùng {@code entity} (bảng CSDL) và
 * {@code dto} (dữ liệu vào/ra API). Xem .context/project_structure.md §4.4.
 */
package com.ctv.flowtracer.app.scan;
