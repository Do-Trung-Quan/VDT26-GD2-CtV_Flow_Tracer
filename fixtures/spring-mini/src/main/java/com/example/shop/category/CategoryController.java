package com.example.shop.category;

import com.example.shop.common.ApiPaths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.CATEGORIES)
@Tag(name = "Danh mục", description = "Quản lý danh mục sản phẩm")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "Lấy danh sách danh mục")
    @GetMapping
    public List<CategoryResponse> list(
            @RequestParam(defaultValue = "1") int page, @RequestParam(required = false) String keyword) {
        return categoryService.list(page, keyword);
    }

    /**
     * Lấy chi tiết một danh mục. Trả lỗi nếu không tồn tại.
     */
    @GetMapping("/{id:\\d+}")
    public CategoryResponse get(@PathVariable Long id) {
        return categoryService.get(id);
    }

    @Operation(summary = "Tạo danh mục", description = "Mã danh mục không được trùng")
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(categoryService.create(request)));
    }

    @DeleteMapping(path = "/{id}")
    public void delete(@PathVariable("id") Long categoryId) {
        categoryService.delete(categoryId);
    }

    @RequestMapping(value = "/export", method = {RequestMethod.GET, RequestMethod.POST})
    public byte[] export() {
        return categoryService.export();
    }
}
