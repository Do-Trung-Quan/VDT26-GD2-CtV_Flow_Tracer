package com.example.flow.product;

/** Interface có hai lớp hiện thực, không lớp nào là @Primary → nơi gọi không xác định được đích. */
public interface ProductQueryService {

    long countByCategoryId(Long categoryId);
}
