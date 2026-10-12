package com.example.shop.order;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/")
public class OrderController {

    @GetMapping
    public List<OrderResponse> list(@RequestHeader("X-Shop-Id") String shopId) {
        return List.of();
    }

    @PutMapping(path = "/{id}/status")
    public OrderResponse changeStatus(@PathVariable Long id, @RequestBody OrderStatusRequest request) {
        return null;
    }

    @RequestMapping("/sync")
    public void sync() {}

    @GetMapping("${shop.order.export-path:/export}")
    public String export() {
        return "";
    }

    @GetMapping("${shop.order.archive-path}")
    public String archive() {
        return "";
    }

    @GetMapping("${shop.order.missing-path}")
    public String missing() {
        return "";
    }

    @GetMapping("${shop.order.env-path}")
    public String env() {
        return "";
    }

    @GetMapping("${shop.order.api-token}")
    public String secret() {
        return "";
    }

    @GetMapping("${shop.order.token-path}")
    public String tokenPath() {
        return "";
    }

    @GetMapping("${shop.order.profile-path}")
    public String profile() {
        return "";
    }

    @GetMapping(ExternalPaths.REPORT)
    public String report() {
        return "";
    }
}
