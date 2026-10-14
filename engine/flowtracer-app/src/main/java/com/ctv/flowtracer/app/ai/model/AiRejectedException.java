package com.ctv.flowtracer.app.ai.model;

/** Đầu ra của AI không đạt kiểm tra (sai cấu trúc, lệch số bước, nhắc tới thứ không có thật…): bị bỏ, dùng kết quả tất định. */
public class AiRejectedException extends RuntimeException {

    public AiRejectedException(String message) {
        super(message);
    }
}
