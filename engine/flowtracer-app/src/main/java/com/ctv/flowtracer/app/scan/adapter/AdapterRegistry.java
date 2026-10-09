package com.ctv.flowtracer.app.scan.adapter;

import com.ctv.flowtracer.adapter.spring.SpringStackAdapter;
import com.ctv.flowtracer.ir.SourceRole;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * NƠI DUY NHẤT đăng ký adapter (CLAUDE.md §3.2a).
 *
 * <p>TODO (mở rộng): "php-legacy".
 */
@Component
public class AdapterRegistry {

    private final List<StackAdapter> adapters;

    public AdapterRegistry(SidecarRunner sidecarRunner, JsonMapper jsonMapper) {
        this.adapters = List.of(
                new SpringStackAdapter(),
                new SidecarStackAdapter(
                        "angular",
                        Set.of(SourceRole.FRONTEND),
                        List.of("angular.json"),
                        List.of("angular.json", "package.json"),
                        sidecarRunner,
                        jsonMapper));
    }

    public List<StackAdapter> all() {
        return adapters;
    }

    public Optional<StackAdapter> byId(String id) {
        return adapters.stream().filter(a -> a.id().equals(id)).findFirst();
    }
}
