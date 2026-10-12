package com.example.flow.product;

import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl implements ProductQueryService {

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public long countByCategoryId(Long categoryId) {
        return productRepository.countByCategory(categoryId);
    }
}
