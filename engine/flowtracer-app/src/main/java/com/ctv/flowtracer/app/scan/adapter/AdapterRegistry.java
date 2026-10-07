package com.ctv.flowtracer.app.scan.adapter;

import com.ctv.flowtracer.adapter.spring.SpringStackAdapter;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * NƠI DUY NHẤT đăng ký adapter (CLAUDE.md §3.2a).
 *
 * <p>TODO (phase phân tích Angular): thêm {@code SidecarStackAdapter("angular")}; (mở rộng) "php-legacy".
 */
@Component
public class AdapterRegistry {

    private final List<StackAdapter> adapters = List.of(new SpringStackAdapter());

    public List<StackAdapter> all() {
        return adapters;
    }

    public Optional<StackAdapter> byId(String id) {
        return adapters.stream().filter(a -> a.id().equals(id)).findFirst();
    }
}
