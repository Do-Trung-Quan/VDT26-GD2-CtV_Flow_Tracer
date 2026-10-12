package com.example.flow.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query(value = "select count(*) from product where category_id = :categoryId", nativeQuery = true)
    long countByCategory(Long categoryId);
}
