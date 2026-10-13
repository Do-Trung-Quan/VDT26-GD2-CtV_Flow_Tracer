package com.ctv.flowtracer.app.graph.service;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.graph.dto.response.OpenApiCheckResponse;
import com.ctv.flowtracer.app.graph.dto.response.OpenApiOperationResponse;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.repository.IrNodeRepository;
import com.ctv.flowtracer.app.source.SourceReader;
import com.ctv.flowtracer.app.workspace.model.SourceScanTarget;
import com.ctv.flowtracer.app.workspace.service.WorkspaceService;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceRole;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * Đối chiếu OpenAPI (API C6): so API tìm được từ code với đặc tả OpenAPI 3 / Swagger 2 — của file {@code openapi.*} /
 * {@code swagger.*} trong Source Backend, hoặc của một địa chỉ URL do người dùng cung cấp. Cho biết API khớp, chỉ có trong
 * code, chỉ có trong đặc tả; dùng để biết tool có bỏ sót API không (NFR-07) hoặc đặc tả có lỗi thời không. Không chạy dự án;
 * chỉ đọc đặc tả.
 */
@Service
public class OpenApiCheckService {

    private static final Pattern SPEC_FILE = Pattern.compile("(?i)(openapi|swagger)[^/]*\\.(ya?ml|json)");
    private static final Set<String> SKIPPED_DIRS = Set.of("node_modules", "target", "build", "dist", ".git", ".idea", "vendor");
    private static final long MAX_SPEC_BYTES = 10L * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final List<String> METHODS = List.of("get", "post", "put", "delete", "patch", "head", "options");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Một thao tác trong đặc tả: đường dẫn gốc và các biến thể dùng để so khớp. */
    private record SpecOperation(String method, String path, List<String> variants, String summary) {}

    private final IrNodeRepository nodes;
    private final WorkspaceService workspaceService;
    private final SourceReader reader;

    public OpenApiCheckService(IrNodeRepository nodes, WorkspaceService workspaceService, SourceReader reader) {
        this.nodes = nodes;
        this.workspaceService = workspaceService;
        this.reader = reader;
    }

    /**
     * @param openapiUrl địa chỉ đặc tả; null / rỗng thì tự tìm trong Source Backend
     * @throws AppException {@code WORKSPACE_NOT_FOUND}; {@code OPENAPI_NOT_FOUND} nếu không có file đặc tả trong repo;
     *     {@code OPENAPI_INVALID} nếu nội dung không phải OpenAPI 3 / Swagger 2 hoặc không tải được địa chỉ
     */
    @Transactional(readOnly = true)
    public OpenApiCheckResponse check(UUID workspaceId, String openapiUrl) {
        workspaceService.requireExists(workspaceId);
        List<SourceScanTarget> backends = workspaceService.scanTargets(workspaceId).stream()
                .filter(s -> s.role() == SourceRole.BACKEND)
                .toList();
        String source;
        String text;
        if (openapiUrl != null && !openapiUrl.isBlank()) {
            source = openapiUrl.strip();
            text = fetch(source);
        } else {
            source = findSpecFile(backends);
            text = reader.readString(source);
        }
        Map<String, Object> spec = parse(text, source);
        List<SpecOperation> specOps = operations(spec);
        List<IrNodeRow> endpoints = new ArrayList<>();
        for (SourceScanTarget s : backends) {
            endpoints.addAll(nodes.findByKind(workspaceId, NodeKind.API_ENDPOINT, List.of(s.sourceId())));
        }

        var matched = new ArrayList<OpenApiOperationResponse>();
        var onlyInCode = new ArrayList<OpenApiOperationResponse>();
        Set<SpecOperation> used = new HashSet<>();
        for (IrNodeRow row : endpoints) {
            IrNode ep = row.node();
            String method = String.valueOf(ep.attrs().getOrDefault("httpMethod", "")).toUpperCase(Locale.ROOT);
            String path = String.valueOf(ep.attrs().getOrDefault("path", ""));
            List<String> codeVariants = codeVariants(ep);
            SpecOperation hit = specOps.stream()
                    .filter(o -> !used.contains(o) && (method.equals("ANY") || o.method().equals(method))
                            && o.variants().stream().anyMatch(codeVariants::contains))
                    .findFirst()
                    .orElse(null);
            var response = new OpenApiOperationResponse(method, path, ep.key(), summaryOf(ep));
            if (hit != null) {
                used.add(hit);
                matched.add(response);
            } else {
                onlyInCode.add(response);
            }
        }
        var onlyInSpec = specOps.stream()
                .filter(o -> !used.contains(o))
                .map(o -> new OpenApiOperationResponse(o.method(), o.path(), null, o.summary()))
                .toList();
        Comparator<OpenApiOperationResponse> order = Comparator.comparing(OpenApiOperationResponse::path).thenComparing(OpenApiOperationResponse::method);
        matched.sort(order);
        onlyInCode.sort(order);
        return new OpenApiCheckResponse(
                source, version(spec), endpoints.size(), specOps.size(), matched, onlyInCode, onlyInSpec.stream().sorted(order).toList());
    }

    // ---------------- lấy đặc tả ----------------

    private String findSpecFile(List<SourceScanTarget> backends) {
        var found = new ArrayList<String>();
        for (SourceScanTarget s : backends) {
            String root = s.rootPath() == null ? "" : s.rootPath();
            reader.walkFiles(root, dir -> !SKIPPED_DIRS.contains(dir.substring(dir.lastIndexOf('/') + 1)),
                            (path, size) -> size <= MAX_SPEC_BYTES && SPEC_FILE.matcher(path.substring(path.lastIndexOf('/') + 1)).matches())
                    .forEach(e -> found.add(e.relativePath()));
        }
        // ưu tiên file openapi.* trước swagger.*, rồi theo đường dẫn
        return found.stream()
                .sorted(Comparator.comparingInt((String p) -> p.toLowerCase(Locale.ROOT).contains("openapi") ? 0 : 1).thenComparing(Comparator.naturalOrder()))
                .findFirst()
                .orElseThrow(() -> new AppException(
                        ErrorCode.OPENAPI_NOT_FOUND,
                        "Không thấy file đặc tả (openapi.yaml/json, swagger.yaml/json) trong mã nguồn Backend; hãy nhập địa chỉ đặc tả của Backend đang chạy"));
    }

    private static String fetch(String url) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).header("Accept", "application/json, application/yaml").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AppException(ErrorCode.OPENAPI_INVALID, "Không tải được đặc tả: máy chủ trả mã " + response.statusCode());
            }
            if (response.body().length() > MAX_SPEC_BYTES) {
                throw new AppException(ErrorCode.OPENAPI_INVALID, "Đặc tả quá lớn (tối đa 10 MB)");
            }
            return response.body();
        } catch (IOException | IllegalArgumentException e) {
            throw new AppException(ErrorCode.OPENAPI_INVALID, "Không tải được đặc tả từ địa chỉ này: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.OPENAPI_INVALID, "Việc tải đặc tả bị gián đoạn");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String text, String source) {
        Object parsed;
        try {
            if (text.stripLeading().startsWith("{")) {
                parsed = JSON.readValue(text, Map.class);
            } else {
                var options = new LoaderOptions();
                options.setMaxAliasesForCollections(50);
                options.setCodePointLimit((int) MAX_SPEC_BYTES);
                parsed = new Yaml(new SafeConstructor(options)).load(text);
            }
        } catch (RuntimeException e) {
            throw new AppException(ErrorCode.OPENAPI_INVALID, "Nội dung " + source + " không đọc được (không phải JSON / YAML hợp lệ)");
        }
        if (!(parsed instanceof Map<?, ?> map) || !(map.get("paths") instanceof Map<?, ?>)
                || !(map.get("openapi") instanceof String o && o.startsWith("3") || "2.0".equals(String.valueOf(map.get("swagger"))))) {
            throw new AppException(ErrorCode.OPENAPI_INVALID, "Nội dung không phải đặc tả OpenAPI 3 hoặc Swagger 2");
        }
        return (Map<String, Object>) map;
    }

    private static String version(Map<String, Object> spec) {
        return spec.get("openapi") != null ? "OpenAPI " + spec.get("openapi") : "Swagger " + spec.get("swagger");
    }

    // ---------------- so khớp ----------------

    @SuppressWarnings("unchecked")
    private static List<SpecOperation> operations(Map<String, Object> spec) {
        String prefix = basePath(spec);
        var out = new ArrayList<SpecOperation>();
        ((Map<String, Object>) spec.get("paths")).forEach((path, item) -> {
            if (!(item instanceof Map<?, ?> methods)) {
                return;
            }
            for (String m : METHODS) {
                if (methods.get(m) instanceof Map<?, ?> op) {
                    Set<String> variants = new LinkedHashSet<>();
                    variants.add(normalize(path));
                    if (!prefix.isEmpty()) {
                        variants.add(normalize(prefix + path));
                    }
                    out.add(new SpecOperation(m.toUpperCase(Locale.ROOT), path, List.copyOf(variants),
                            op.get("summary") instanceof String s ? s : null));
                }
            }
        });
        return out;
    }

    /** Tiền tố chung của mọi đường dẫn: {@code basePath} (Swagger 2) hoặc phần đường dẫn của {@code servers[0].url} (OpenAPI 3). */
    @SuppressWarnings("unchecked")
    private static String basePath(Map<String, Object> spec) {
        if (spec.get("basePath") instanceof String b) {
            return b;
        }
        if (spec.get("servers") instanceof List<?> servers && !servers.isEmpty() && servers.get(0) instanceof Map<?, ?> s
                && s.get("url") instanceof String url && !url.contains("{")) {
            String noScheme = url.replaceFirst("^(?i)https?://[^/]*", "");
            return noScheme.equals("/") ? "" : noScheme;
        }
        return "";
    }

    /** Đường dẫn của API trong code, có và không có context-path (đặc tả thường ghi không kèm context-path). */
    private static List<String> codeVariants(IrNode endpoint) {
        String path = String.valueOf(endpoint.attrs().getOrDefault("path", ""));
        Set<String> variants = new LinkedHashSet<>();
        variants.add(normalize(path));
        if (endpoint.attrs().get("contextPath") instanceof String ctx && !ctx.isBlank() && path.startsWith(ctx)) {
            variants.add(normalize(path.substring(ctx.length())));
        }
        return List.copyOf(variants);
    }

    /** Biến đường dẫn {@code {id}} thành {@code {}}, bỏ '/' thừa và ở cuối, để {@code /a/{id}} và {@code /a/{code}} khớp nhau. */
    static String normalize(String path) {
        String p = path.replaceAll("\\{[^}]*}", "{}").replaceAll("/{2,}", "/");
        return p.length() > 1 && p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    private static String summaryOf(IrNode endpoint) {
        return endpoint.displayName() != null && !endpoint.displayName().isBlank() ? endpoint.displayName() : null;
    }
}
