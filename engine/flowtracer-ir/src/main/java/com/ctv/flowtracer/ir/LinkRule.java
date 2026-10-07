package com.ctv.flowtracer.ir;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * Luật dùng khi nối lời gọi HTTP ở FE với endpoint ở BE, hoặc luật nhận diện truy vấn.
 *
 * @param type loại luật
 * @param pattern mẫu cần khớp (tiền tố, regex của pathRewrite, context-path, hoặc {@code FQN#method} với QUERY_SINK)
 * @param replacement giá trị thay thế (chỉ dùng với REWRITE)
 * @param origin luật tự phát hiện hay do người dùng nhập
 * @param description mô tả ngắn, vd "proxy.conf.json: ^/gateway → ''"
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkRule(Type type, String pattern, String replacement, Origin origin, String description) {

    public enum Type {
        PREFIX,
        REWRITE,
        CONTEXT_PATH,
        QUERY_SINK
    }

    public enum Origin {
        AUTO,
        MANUAL
    }

    public LinkRule {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(origin, "origin");
    }
}
