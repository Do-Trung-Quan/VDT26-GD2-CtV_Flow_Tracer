package com.ctv.flowtracer.ir;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * Điều kiện bao quanh một lời gọi hoặc câu lệnh throw. Dùng để vẽ khối alt/opt trên sequence và sinh mục
 * "Ràng buộc".
 *
 * @param type loại cấu trúc điều khiển
 * @param text điều kiện dạng văn bản đã rút gọn (vd {@code repo.existsByCode(code)})
 * @param branch nhánh chứa lời gọi (vd {@code then}, {@code else}, {@code catch:IOException})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Guard(Type type, String text, String branch) {

    public enum Type {
        IF,
        ELSE,
        SWITCH,
        TERNARY,
        TRY,
        CATCH,
        LOOP
    }

    public Guard {
        Objects.requireNonNull(type, "type");
    }
}
