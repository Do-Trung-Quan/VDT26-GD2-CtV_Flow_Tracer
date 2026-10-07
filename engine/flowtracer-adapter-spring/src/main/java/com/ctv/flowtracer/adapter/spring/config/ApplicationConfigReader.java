package com.ctv.flowtracer.adapter.spring.config;

import com.ctv.flowtracer.ir.spi.ResolveContext;

/** Chỉ đọc đúng các khóa cần thiết trong application*.yml/.properties (context-path, Jackson naming). KHÔNG giữ giá trị bí mật. */
public final class ApplicationConfigReader {

    /** Cấu hình cần cho phân tích. */
    public record AppConfig(String contextPath, String jsonNamingStrategy) {}

    public AppConfig read(ResolveContext ctx) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
