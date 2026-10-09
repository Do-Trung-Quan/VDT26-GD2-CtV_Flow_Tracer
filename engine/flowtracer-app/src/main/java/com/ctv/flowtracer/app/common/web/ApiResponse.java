package com.ctv.flowtracer.app.common.web;

import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Vỏ bọc chuẩn cho MỌI response REST của Engine (thành công và lỗi). Dữ liệu nghiệp vụ luôn nằm trong {@code data}.
 *
 * <pre>
 * { "status": 200, "code": "OK", "message": "Thành công", "data": { ... } }
 * { "status": 409, "code": "WORKSPACE_NAME_TAKEN", "message": "Tên Workspace đã được dùng", "data": null,
 *   "errors": { "name": "Shop" } }
 * </pre>
 *
 * <p>Ngoại lệ: luồng SSE (tiến độ quét, nội dung AI đang sinh) và {@code /api/health} của Actuator không bọc.
 *
 * @param status mã HTTP, trùng với status của response
 * @param code {@code OK} / {@code CREATED} khi thành công, tên {@link ErrorCode} khi lỗi
 * @param message thông điệp tiếng Việt cho người dùng
 * @param data dữ liệu trả về; {@code null} khi lỗi hoặc thao tác không có dữ liệu (vd xóa)
 * @param errors chi tiết lỗi (trường sai → lý do, hoặc ngữ cảnh lỗi); bỏ qua khi rỗng
 * @param <T> kiểu dữ liệu
 */
public record ApiResponse<T>(
        int status,
        String code,
        String message,
        T data,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> errors) {

    public static final String CODE_OK = "OK";
    public static final String CODE_CREATED = "CREATED";

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, "Thành công");
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(HttpStatus.OK.value(), CODE_OK, message, data, Map.of());
    }

    public static <T> ApiResponse<T> created(T data, String message) {
        return new ApiResponse<>(HttpStatus.CREATED.value(), CODE_CREATED, message, data, Map.of());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, Map<String, Object> errors) {
        return new ApiResponse<>(code.status().value(), code.name(), message, null, errors == null ? Map.of() : errors);
    }
}
