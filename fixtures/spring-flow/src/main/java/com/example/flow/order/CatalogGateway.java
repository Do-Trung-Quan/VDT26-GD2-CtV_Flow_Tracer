package com.example.flow.order;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** Lấy danh sách sản phẩm từ service sản phẩm (cùng Workspace) bằng WebClient — liên kết Backend → Backend. */
@Component
public class CatalogGateway {

    private final WebClient catalogWebClient;

    public CatalogGateway(WebClient catalogWebClient) {
        this.catalogWebClient = catalogWebClient;
    }

    public String productOptions() {
        return catalogWebClient.get().uri("/api/v1/products/options").retrieve().bodyToMono(String.class).block();
    }
}
