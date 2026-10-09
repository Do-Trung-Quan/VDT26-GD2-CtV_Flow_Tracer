package com.ctv.flowtracer.app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Mọi {@code @RestController} của tool được gắn tiền tố {@code /api}. Web UI (file tĩnh trong {@code classpath:/static})
 * được phục vụ tại "/".
 *
 * <p>TODO (phase Web UI): chuyển các route SPA (vd {@code /w/**}) về {@code index.html}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Chỉ controller của tool; controller của thư viện (springdoc /v3/api-docs) giữ nguyên đường dẫn
        configurer.addPathPrefix(
                API_PREFIX,
                HandlerTypePredicate.forBasePackage("com.ctv.flowtracer.app").and(HandlerTypePredicate.forAnnotation(RestController.class)));
    }
}
