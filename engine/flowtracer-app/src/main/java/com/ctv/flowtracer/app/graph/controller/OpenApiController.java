package com.ctv.flowtracer.app.graph.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.graph.dto.request.OpenApiCheckRequest;
import com.ctv.flowtracer.app.graph.dto.response.OpenApiCheckResponse;
import com.ctv.flowtracer.app.graph.service.OpenApiCheckService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API C6: đối chiếu API tìm được từ code với đặc tả OpenAPI. */
@RestController
@RequestMapping("/workspaces/{id}/openapi-check")
public class OpenApiController {

    private final OpenApiCheckService openApiCheckService;

    public OpenApiController(OpenApiCheckService openApiCheckService) {
        this.openApiCheckService = openApiCheckService;
    }

    /** C6 — không có URL thì tự tìm file đặc tả trong repo; nội dung không phải OpenAPI 3 / Swagger 2 thì trả {@code 422}. */
    @PostMapping
    public ApiResponse<OpenApiCheckResponse> check(@PathVariable UUID id, @Valid @RequestBody(required = false) OpenApiCheckRequest request) {
        return ApiResponse.ok(openApiCheckService.check(id, request == null ? null : request.openapiUrl()));
    }
}
