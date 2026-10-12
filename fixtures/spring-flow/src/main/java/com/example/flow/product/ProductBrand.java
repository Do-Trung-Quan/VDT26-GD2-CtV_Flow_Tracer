package com.example.flow.product;

import jakarta.persistence.Entity;

/** Entity không khai @Table: tên bảng phải suy ra theo quy ước đặt tên (product_brand). */
@Entity
public class ProductBrand {

    private Long id;
    private String name;
}
