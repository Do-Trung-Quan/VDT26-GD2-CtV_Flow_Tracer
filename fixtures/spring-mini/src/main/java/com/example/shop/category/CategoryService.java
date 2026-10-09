package com.example.shop.category;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {

    public List<CategoryResponse> list(int page, String keyword) {
        return List.of();
    }

    public CategoryResponse get(Long id) {
        return null;
    }

    public CategoryResponse create(CategoryRequest request) {
        return null;
    }

    public void delete(Long id) {}

    public byte[] export() {
        return new byte[0];
    }
}
