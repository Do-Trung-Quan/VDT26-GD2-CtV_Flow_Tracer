package com.example.flow.category;

import java.util.List;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    List<CategoryResponse> search(String keyword);

    boolean delete(Long id);
}
