package com.ctv.flowtracer.app.workspace.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.common.web.ValidationPatterns;
import com.ctv.flowtracer.app.workspace.dto.response.FolderBrowseResponse;
import com.ctv.flowtracer.app.workspace.service.FolderBrowseService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API A6: duyệt thư mục bên trong {@code SOURCE_ROOT}. */
@RestController
public class FolderBrowseController {

    private final FolderBrowseService folderBrowseService;

    public FolderBrowseController(FolderBrowseService folderBrowseService) {
        this.folderBrowseService = folderBrowseService;
    }

    /** @param path đường dẫn tương đối trong {@code SOURCE_ROOT}; bỏ trống là chính {@code SOURCE_ROOT} */
    @GetMapping("/fs/browse")
    public ApiResponse<FolderBrowseResponse> browse(
            @RequestParam(defaultValue = "")
                    @Size(max = 1000, message = "Đường dẫn tối đa 1000 ký tự")
                    @Pattern(
                            regexp = ValidationPatterns.RELATIVE_PATH,
                            message = "Đường dẫn phải là đường dẫn tương đối trong SOURCE_ROOT, không chứa '..'")
                    String path) {
        return ApiResponse.ok(folderBrowseService.browse(path));
    }
}
