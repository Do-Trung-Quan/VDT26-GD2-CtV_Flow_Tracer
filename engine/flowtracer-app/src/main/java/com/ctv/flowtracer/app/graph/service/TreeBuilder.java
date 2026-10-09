package com.ctv.flowtracer.app.graph.service;

import com.ctv.flowtracer.app.graph.dto.response.ApiModuleResponse;
import com.ctv.flowtracer.app.graph.dto.response.BackendTreeResponse;
import com.ctv.flowtracer.app.graph.dto.response.EndpointNodeResponse;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.ir.IrNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Dựng cây API Backend {@code Module → Endpoint} từ node {@code API_ENDPOINT} đã lưu. Module và mô tả lấy nguyên từ
 * thuộc tính bộ phân tích ghi lại ({@code module}, {@code moduleSource}, {@code summarySource}); không suy đoán thêm.
 */
final class TreeBuilder {

    private static final List<String> METHOD_ORDER = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "ANY");

    private static final Comparator<EndpointNodeResponse> ENDPOINT_ORDER = Comparator.comparing(EndpointNodeResponse::path)
            .thenComparing(e -> methodRank(e.httpMethod()))
            .thenComparing(EndpointNodeResponse::httpMethod);

    private TreeBuilder() {}

    /**
     * @param endpoints node API_ENDPOINT của các Source Backend
     * @param sourceNames id Source → tên
     */
    static BackendTreeResponse backend(List<IrNodeRow> endpoints, Map<UUID, String> sourceNames) {
        // (tên module, Source) → endpoint; TreeMap để thứ tự ổn định
        Map<String, Group> groups = new TreeMap<>();
        for (IrNodeRow row : endpoints) {
            IrNode node = row.node();
            String module = text(node.attrs().get("module"), "Khác");
            String key = module + "\u0001" + row.sourceId();
            groups.computeIfAbsent(key, k -> new Group(module, text(node.attrs().get("moduleSource"), null), row.sourceId()))
                    .endpoints()
                    .add(endpoint(node));
        }
        var modules = new ArrayList<ApiModuleResponse>();
        for (Group g : groups.values()) {
            List<EndpointNodeResponse> sorted = g.endpoints().stream().sorted(ENDPOINT_ORDER).toList();
            modules.add(new ApiModuleResponse(
                    g.name(), g.nameSource(), g.sourceId(), sourceNames.get(g.sourceId()), sorted.size(), sorted));
        }
        return new BackendTreeResponse(modules.size(), endpoints.size(), List.copyOf(modules));
    }

    private static EndpointNodeResponse endpoint(IrNode node) {
        Map<String, Object> attrs = node.attrs();
        var warnings = new ArrayList<String>();
        if (Boolean.FALSE.equals(attrs.get("pathResolved"))) {
            warnings.add("Đường dẫn chứa hằng số chưa giải được");
        }
        return new EndpointNodeResponse(
                node.key(),
                text(attrs.get("httpMethod"), "ANY"),
                text(attrs.get("path"), node.name()),
                node.displayName(),
                text(attrs.get("summarySource"), null),
                Boolean.TRUE.equals(attrs.get("deprecated")),
                node.location() == null ? null : node.location().filePath(),
                node.location() == null ? null : node.location().startLine(),
                List.copyOf(warnings));
    }

    private static int methodRank(String method) {
        int i = METHOD_ORDER.indexOf(method);
        return i < 0 ? METHOD_ORDER.size() : i;
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private record Group(String name, String nameSource, UUID sourceId, List<EndpointNodeResponse> endpoints) {
        Group(String name, String nameSource, UUID sourceId) {
            this(name, nameSource, sourceId, new ArrayList<>());
        }
    }
}
