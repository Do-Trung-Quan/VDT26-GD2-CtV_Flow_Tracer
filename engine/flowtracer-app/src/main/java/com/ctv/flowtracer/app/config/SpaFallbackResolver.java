package com.ctv.flowtracer.app.config;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Phục vụ web UI (Angular, đã build vào {@code classpath:/static}): file có thật thì trả về; đường dẫn của một màn hình (vd {@code /w/<id>},
 * {@code /settings/ai}) thì trả {@code index.html} để router phía trình duyệt xử lý khi tải lại trang hoặc mở đường dẫn trực tiếp. Đường dẫn
 * API ({@code api/}, {@code v3/}) và đường dẫn trỏ tới một file (có dấu chấm, vd {@code chunk-abc.js}) mà không tồn tại thì {@code null} (404).
 */
public class SpaFallbackResolver extends PathResourceResolver {

    private static final String INDEX = "index.html";

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        Resource requested = location.createRelative(resourcePath);
        if (requested.exists() && requested.isReadable()) {
            return requested;
        }
        if (resourcePath.startsWith("api/") || resourcePath.startsWith("v3/") || resourcePath.startsWith("swagger-ui") || resourcePath.contains(".")) {
            return null;
        }
        Resource index = location.createRelative(INDEX);
        return index.exists() && index.isReadable() ? index : null;
    }

    /** Dùng cho test: cùng quy tắc với {@link #getResource(String, Resource)}. */
    public Resource resolveIn(String resourcePath, Resource location) throws IOException {
        return getResource(resourcePath, location);
    }
}
