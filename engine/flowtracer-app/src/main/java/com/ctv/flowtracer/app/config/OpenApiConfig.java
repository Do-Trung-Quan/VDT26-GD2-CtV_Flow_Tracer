package com.ctv.flowtracer.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Thông tin OpenAPI. Web UI sinh kiểu TypeScript từ {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI flowTracerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CtV Flow Tracer – Engine API")
                        .version("0.1.0")
                        .description("API nội bộ giữa Web UI và Analysis Engine. Xem .context/API_list.md"));
    }
}
