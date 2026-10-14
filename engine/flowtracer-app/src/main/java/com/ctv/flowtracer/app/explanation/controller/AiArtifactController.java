package com.ctv.flowtracer.app.explanation.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.explanation.dto.request.GenerateAiRequest;
import com.ctv.flowtracer.app.explanation.dto.response.AiGenerationResponse;
import com.ctv.flowtracer.app.explanation.service.AiArtifactService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API F4: sinh / sinh lại nội dung AI cho một đối tượng. */
@RestController
@RequestMapping("/workspaces/{id}")
public class AiArtifactController {

    private final AiArtifactService service;

    public AiArtifactController(AiArtifactService service) {
        this.service = service;
    }

    @PostMapping("/ai-artifacts/regenerate")
    public ApiResponse<AiGenerationResponse> regenerate(@PathVariable UUID id, @Valid @RequestBody GenerateAiRequest request) {
        return ApiResponse.ok(service.generate(id, request));
    }
}
