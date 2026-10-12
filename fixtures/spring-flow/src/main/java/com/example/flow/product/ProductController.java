package com.example.flow.product;

import com.example.flow.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/options")
    public ApiResponse<List<Product>> options() {
        return ApiResponse.ok(productService.getOptions());
    }

    @GetMapping
    public ApiResponse<List<Product>> search(@RequestParam String keyword, @RequestParam boolean withBrand) {
        return ApiResponse.ok(productService.search(keyword, withBrand));
    }
}
