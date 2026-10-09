package com.ctv.flowtracer.app.workspace.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.workspace.dto.request.DetectFolderRequest;
import com.ctv.flowtracer.app.workspace.dto.response.DetectionResponse;
import com.ctv.flowtracer.app.workspace.service.StackDetectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API A10: nhận diện thư mục người dùng chọn, trước khi lưu. */
@RestController
public class DetectionController {

    private final StackDetectionService stackDetectionService;

    public DetectionController(StackDetectionService stackDetectionService) {
        this.stackDetectionService = stackDetectionService;
    }

    @PostMapping("/fs/detect")
    public ApiResponse<DetectionResponse> detect(@Valid @RequestBody DetectFolderRequest request) {
        return ApiResponse.ok(stackDetectionService.detectResponse(request.rootPath()));
    }
}
