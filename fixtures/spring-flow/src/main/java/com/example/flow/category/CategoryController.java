package com.example.flow.category;

import com.example.flow.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Danh mục")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "Thêm mới danh mục")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @PostMapping
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.create(request)));
    }

    @Operation(summary = "Tìm kiếm danh mục")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> search(@RequestParam String keyword) {
        return ApiResponse.ok(categoryService.search(keyword));
    }

    @Operation(summary = "Xóa danh mục")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        return ApiResponse.ok(categoryService.delete(id));
    }
}
