package com.ctv.flowtracer.app.scan.adapter;

import com.ctv.flowtracer.adapter.spring.SpringStackAdapter;
import com.ctv.flowtracer.ir.SourceRole;
import com.ctv.flowtracer.ir.spi.StackAdapter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * NƠI DUY NHẤT đăng ký adapter (CLAUDE.md §3.2a).
 *
 * <p>TODO (mở rộng): "php-legacy".
 */
@Component
public class AdapterRegistry {

    /**
     * Phiên bản bộ phân tích Angular: tăng khi cách trích dữ kiện hoặc luật ghép trong
     * {@code analyzer-node/src/adapters/angular} đổi (cùng với {@code FACTS_VERSION} trong {@code facts.ts} khi dữ kiện đổi).
     */
    static final String ANGULAR_ANALYSIS_VERSION = "3";

    private static final Pattern ANGULAR_CONFIG_JSON =
            Pattern.compile("(^|/)(angular\\.json|package\\.json|tsconfig[^/]*\\.json|proxy[^/]*\\.json)$");
    private static final Pattern ANGULAR_FEDERATION = Pattern.compile("(^|/)federation\\.config\\.(js|cjs|mjs)$");
    private static final Pattern VI_I18N_JSON = Pattern.compile("(^|[/._-])vi([-_][A-Za-z]{2})?\\.json$");

    private final List<StackAdapter> adapters;

    public AdapterRegistry(SidecarRunner sidecarRunner, JsonMapper jsonMapper) {
        this.adapters = List.of(
                new SpringStackAdapter(),
                new SidecarStackAdapter(
                        "angular",
                        Set.of(SourceRole.FRONTEND),
                        List.of("angular.json"),
                        List.of("angular.json", "package.json"),
                        AdapterRegistry::analyzesAngular,
                        ANGULAR_ANALYSIS_VERSION,
                        sidecarRunner,
                        jsonMapper));
    }

    public List<StackAdapter> all() {
        return adapters;
    }

    public Optional<StackAdapter> byId(String id) {
        return adapters.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    /**
     * File của Source Angular được đưa vào sidecar: mã TypeScript và template (bỏ {@code .spec.ts}, {@code .d.ts}), file cấu
     * hình cần đọc ({@code angular.json}, tsconfig, proxy, package.json, {@code federation.config.js}) và file ngôn ngữ tiếng
     * Việt dạng JSON. Không chạy hay thực thi file nào.
     */
    static boolean analyzesAngular(String path) {
        if (path.endsWith(".spec.ts") || path.endsWith(".d.ts")) {
            return false;
        }
        if (path.endsWith(".ts") || path.endsWith(".html")) {
            return true;
        }
        if (path.endsWith(".json")) {
            return ANGULAR_CONFIG_JSON.matcher(path).find() || VI_I18N_JSON.matcher(path).find();
        }
        return ANGULAR_FEDERATION.matcher(path).find();
    }
}
