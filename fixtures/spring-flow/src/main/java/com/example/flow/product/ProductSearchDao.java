package com.example.flow.product;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Truy vấn ghép động bằng StringBuilder, có mảnh nối thêm bảng khi điều kiện đúng. */
@Repository
public class ProductSearchDao {

    private final EntityManager entityManager;

    public ProductSearchDao(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Product> search(String keyword, boolean withBrand) {
        StringBuilder jpql = new StringBuilder("select p from Product p");
        if (withBrand) {
            jpql.append(" join ProductBrand b on b.id = p.brandId");
        }
        jpql.append(" where lower(p.title) like :keyword");
        return entityManager.createQuery(jpql.toString(), Product.class).getResultList();
    }
}
