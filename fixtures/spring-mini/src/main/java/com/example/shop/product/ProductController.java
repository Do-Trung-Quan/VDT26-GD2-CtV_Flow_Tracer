package com.example.shop.product;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

    @GetMapping("/api/v1/products")
    public String list( {
        return "thiếu dấu đóng ngoặc";
    }
}
