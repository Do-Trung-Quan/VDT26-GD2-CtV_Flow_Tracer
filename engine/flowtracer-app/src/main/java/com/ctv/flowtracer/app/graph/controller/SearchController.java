package com.ctv.flowtracer.app.graph.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.graph.dto.response.SearchResponse;
import com.ctv.flowtracer.app.graph.model.SearchCategory;
import com.ctv.flowtracer.app.graph.service.GraphQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API C3: tìm kiếm nhanh (Ctrl+K). */
@RestController
@RequestMapping("/workspaces/{id}/search")
public class SearchController {

    private final GraphQueryService graphQueryService;

    public SearchController(GraphQueryService graphQueryService) {
        this.graphQueryService = graphQueryService;
    }

    /** C3 — kết quả nhóm theo loại thành phần; Workspace chưa quét thì không có kết quả. */
    @GetMapping
    public ApiResponse<SearchResponse> search(
            @PathVariable UUID id,
            @RequestParam @NotBlank(message = "Cần nhập từ khóa tìm kiếm") @Size(max = 100, message = "Từ khóa tối đa 100 ký tự") String q,
            @RequestParam(required = false) List<SearchCategory> kinds,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "Số kết quả mỗi nhóm tối thiểu 1")
                    @Max(value = 50, message = "Số kết quả mỗi nhóm tối đa 50")
                    int limit) {
        return ApiResponse.ok(graphQueryService.search(id, q, kinds, limit));
    }
}
