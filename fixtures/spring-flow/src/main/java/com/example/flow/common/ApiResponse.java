package com.example.flow.common;

/** Vỏ bọc kết quả trả về (envelope chung của mọi API). */
public class ApiResponse<T> {

    private final String message;
    private final T data;

    private ApiResponse(String message, T data) {
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", data);
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
