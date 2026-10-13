package com.example.flow.order;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/** Gọi service kho (không nằm trong Workspace) qua OpenFeign. */
@FeignClient(name = "inventory-service", path = "/api/v1/inventory")
public interface InventoryClient {

    @PostMapping("/{orderId}/reserve")
    void reserve(@PathVariable("orderId") Long orderId);
}
