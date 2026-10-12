package com.example.flow.product;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductSearchDao productSearchDao;

    public ProductService(ProductSearchDao productSearchDao) {
        this.productSearchDao = productSearchDao;
    }

    /** Hàm của Service tên getX() không tham số: là nghiệp vụ thật, không phải getter của DTO. */
    public List<Product> getOptions() {
        return productSearchDao.search("", false);
    }

    public List<Product> search(String keyword, boolean withBrand) {
        return productSearchDao.search(keyword, withBrand);
    }
}
