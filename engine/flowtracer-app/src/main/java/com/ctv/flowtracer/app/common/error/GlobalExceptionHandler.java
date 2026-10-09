package com.ctv.flowtracer.app.common.error;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Chuẩn hóa mọi lỗi về {@link ApiResponse}; ẩn stack trace và thông tin bí mật khỏi response. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_INPUT = "Dữ liệu gửi lên không hợp lệ";

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        return respond(ex.code(), ex.getMessage(), ex.details());
    }

    /** Lỗi Bean Validation trên body: trường → lý do. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(g -> fields.putIfAbsent(g.getObjectName(), g.getDefaultMessage()));
        return respond(ErrorCode.VALIDATION_FAILED, INVALID_INPUT, fields);
    }

    /** Lỗi Bean Validation trên tham số (query, path): tham số → lý do. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParamValidation(HandlerMethodValidationException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> fields.putIfAbsent(
                r.getMethodParameter().getParameterName(),
                r.getResolvableErrors().stream()
                        .map(MessageSourceResolvable::getDefaultMessage)
                        .findFirst()
                        .orElse(INVALID_INPUT)));
        return respond(ErrorCode.VALIDATION_FAILED, INVALID_INPUT, fields);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        return respond(ErrorCode.VALIDATION_FAILED, INVALID_INPUT, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(ErrorCode.NOT_FOUND, "Không tìm thấy đường dẫn", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, "Phương thức HTTP không được hỗ trợ cho đường dẫn này", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Lỗi không mong đợi", ex);
        return respond(ErrorCode.INTERNAL_ERROR, "Đã có lỗi xảy ra, vui lòng xem log của Engine", Map.of());
    }

    private static ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, String message, Map<String, Object> errors) {
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, message, errors));
    }
}
