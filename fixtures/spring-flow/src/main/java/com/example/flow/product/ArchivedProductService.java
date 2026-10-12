package com.example.flow.product;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "shop.product.archive")
public class ArchivedProductService implements ProductQueryService {

    private final ProductRepository productRepository;

    public ArchivedProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public long countByCategoryId(Long categoryId) {
        return productRepository.count();
    }
}
