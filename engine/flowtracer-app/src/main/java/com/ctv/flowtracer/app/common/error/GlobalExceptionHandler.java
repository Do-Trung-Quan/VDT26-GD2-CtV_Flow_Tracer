package com.ctv.flowtracer.app.common.error;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Chuẩn hóa mọi lỗi về {@link ApiError}; ẩn stack trace và thông tin bí mật khỏi response. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleApp(AppException ex) {
        return ResponseEntity.status(ex.code().status()).body(ApiError.of(ex.code(), ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ApiError.of(ErrorCode.VALIDATION_FAILED, "Dữ liệu gửi lên không hợp lệ", fields));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Lỗi không mong đợi", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiError.of(ErrorCode.INTERNAL_ERROR, "Đã có lỗi xảy ra, vui lòng xem log của Engine", Map.of()));
    }
}
