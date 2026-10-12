package com.example.flow.category;

import com.example.flow.common.BusinessException;
import com.example.flow.product.ProductQueryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductQueryService productQueryService;

    public CategoryServiceImpl(CategoryRepository categoryRepository, ProductQueryService productQueryService) {
        this.categoryRepository = categoryRepository;
        this.productQueryService = productQueryService;
    }

    @Override
    public CategoryResponse create(CategoryRequest request) {
        checkPermission();
        String code = normalize(request.getCode());
        if (categoryRepository.existsByCode(code)) {
            throw new BusinessException("CATEGORY_CODE_EXISTS");
        }
        Category entity = new Category();
        entity.setCode(code);
        entity.setTitle(request.getTitle());
        Category saved = categoryRepository.save(entity);
        return toResponse(saved);
    }

    @Override
    public List<CategoryResponse> search(String keyword) {
        return categoryRepository.search(keyword).stream().map(this::toResponse).toList();
    }

    @Override
    public boolean delete(Long id) {
        checkPermission();
        Category category = categoryRepository.findById(id).orElseThrow();
        if (productQueryService.countByCategoryId(id) > 0) {
            throw new BusinessException("CATEGORY_IN_USE");
        }
        category.markDeleted();
        categoryRepository.save(category);
        return true;
    }

    private void checkPermission() {}

    private String normalize(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getCode(), category.getTitle());
    }
}
