package com.ctv.flowtracer.app.graph.service;

import com.ctv.flowtracer.app.graph.dto.response.ApiModuleResponse;
import com.ctv.flowtracer.app.graph.dto.response.BackendTreeResponse;
import com.ctv.flowtracer.app.graph.dto.response.EndpointNodeResponse;
import com.ctv.flowtracer.app.graph.dto.response.FrontendTreeResponse;
import com.ctv.flowtracer.app.graph.dto.response.UiTreeNodeResponse;
import com.ctv.flowtracer.app.graph.model.ContainsRow;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.model.UiLinkRow;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * Dựng cây {@code Ứng dụng → Màn hình → Tab / Hộp thoại / Form → Phần tử} từ các cạnh {@code CONTAINS}. Một thành phần
     * có nhiều nút cha (hộp thoại mở từ hai màn hình, phần tử của thành phần dùng chung) được hiển thị dưới từng nút cha; vòng
     * chứa nhau (hộp thoại tự mở lại chính nó) bị cắt tại lần lặp.
     *
     * @param nodes node giao diện của các Source Frontend
     * @param contains cạnh chứa-trong, đã sắp theo thứ tự trong nút cha
     * @param callingApi key của các thành phần dẫn tới lời gọi HTTP (phần tử khi thao tác; màn hình / hộp thoại / ứng dụng khi mở)
     * @param links hộp thoại / màn hình mà mỗi phần tử dẫn tới (mở hộp thoại, chuyển trang)
     * @param sourceNames id Source → tên
     */
    static FrontendTreeResponse frontend(
            List<IrNodeRow> nodes,
            List<ContainsRow> contains,
            Set<String> callingApi,
            List<UiLinkRow> links,
            Map<UUID, String> sourceNames) {
        var targets = new HashMap<String, List<String>>();
        links.forEach(l -> targets.computeIfAbsent(l.elementKey(), k -> new ArrayList<>()).add(l.targetKey()));
        Map<String, IrNodeRow> byKey = new HashMap<>();
        nodes.forEach(r -> byKey.put(r.node().key(), r));
        Map<String, List<String>> children = new HashMap<>();
        for (ContainsRow c : contains) {
            if (byKey.containsKey(c.parentKey()) && byKey.containsKey(c.childKey())) {
                children.computeIfAbsent(c.parentKey(), k -> new ArrayList<>()).add(c.childKey());
            }
        }
        var ctx = new UiContext(byKey, children, callingApi, targets, new HashMap<>());
        List<UiTreeNodeResponse> apps = nodes.stream()
                .filter(r -> r.node().kind() == NodeKind.UI_APP)
                .sorted(Comparator.comparing((IrNodeRow r) -> r.node().name()).thenComparing(r -> r.node().key()))
                .map(r -> uiNode(r, ctx, sourceNames, new HashSet<>()))
                .toList();
        long screens = nodes.stream().filter(r -> r.node().kind() == NodeKind.UI_SCREEN).count();
        long elements = nodes.stream().filter(r -> r.node().kind() == NodeKind.UI_ELEMENT).count();
        return new FrontendTreeResponse(apps.size(), (int) screens, (int) elements, apps);
    }

    /** Dữ liệu dùng chung khi dựng cây giao diện (bảng tra, kết quả đã tính một lần). */
    private record UiContext(
            Map<String, IrNodeRow> byKey,
            Map<String, List<String>> children,
            Set<String> callingApi,
            Map<String, List<String>> targets,
            Map<String, Boolean> apiBearing) {

        /** Thành phần có gọi API: chính nó gọi khi mở / thao tác, hoặc có thành phần con như vậy (không đi vòng). */
        boolean bearsApi(String key, Set<String> visiting) {
            Boolean known = apiBearing.get(key);
            if (known != null) {
                return known;
            }
            if (!visiting.add(key)) {
                return false;
            }
            boolean result = callingApi.contains(key)
                    || children.getOrDefault(key, List.of()).stream().anyMatch(c -> bearsApi(c, visiting));
            visiting.remove(key);
            if (visiting.isEmpty()) {
                apiBearing.put(key, result);
            }
            return result;
        }

        boolean leadsToApi(String elementKey) {
            return targets.getOrDefault(elementKey, List.of()).stream().anyMatch(t -> bearsApi(t, new HashSet<>()));
        }
    }

    private static UiTreeNodeResponse uiNode(IrNodeRow row, UiContext ctx, Map<UUID, String> sourceNames, Set<String> path) {
        Map<String, IrNodeRow> byKey = ctx.byKey();
        Map<String, List<String>> children = ctx.children();
        Set<String> callingApi = ctx.callingApi();
        IrNode node = row.node();
        var kids = new ArrayList<UiTreeNodeResponse>();
        if (path.add(node.key())) {
            for (String childKey : children.getOrDefault(node.key(), List.of())) {
                if (!path.contains(childKey)) { // vòng chứa nhau: không hiện lại thành phần đang mở
                    kids.add(uiNode(byKey.get(childKey), ctx, sourceNames, path));
                }
            }
            path.remove(node.key());
        }
        boolean element = node.kind() == NodeKind.UI_ELEMENT;
        boolean app = node.kind() == NodeKind.UI_APP;
        return new UiTreeNodeResponse(
                node.key(),
                node.kind(),
                node.name(),
                node.displayName(),
                element && node.displayName() == null ? fallbackName(node) : null,
                element ? text(node.attrs().get("elementType"), null) : null,
                hasApiFlag(node) ? callingApi.contains(node.key()) : null,
                element ? ctx.leadsToApi(node.key()) : null,
                node.kind() == NodeKind.UI_SCREEN ? text(node.attrs().get("routePath"), null) : null,
                app ? row.sourceId() : null,
                app ? sourceNames.get(row.sourceId()) : null,
                node.location() == null ? null : node.location().filePath(),
                node.location() == null ? null : node.location().startLine(),
                List.copyOf(kids));
    }

    /** Cờ "gọi API" chỉ có nghĩa với phần tử, màn hình, hộp thoại và ứng dụng (tab và form chỉ chứa). */
    private static boolean hasApiFlag(IrNode node) {
        return node.kind() == NodeKind.UI_ELEMENT
                || node.kind() == NodeKind.UI_SCREEN
                || node.kind() == NodeKind.UI_DIALOG
                || node.kind() == NodeKind.UI_APP;
    }

    /** Tên kỹ thuật của phần tử không có nhãn: lấy từ chính mã nguồn, không đoán. */
    static String fallbackName(IrNode node) {
        Map<String, Object> attrs = node.attrs();
        String control = text(attrs.get("control"), null);
        if (control != null) {
            return control;
        }
        if (attrs.get("handlers") instanceof List<?> handlers && !handlers.isEmpty()) {
            return String.valueOf(handlers.getFirst());
        }
        String key = text(attrs.get("i18nKey"), null);
        return key != null ? key : text(attrs.get("tag"), node.name());
    }

    private static EndpointNodeResponse endpoint(IrNode node) {
        Map<String, Object> attrs = node.attrs();
        var warnings = new ArrayList<String>();
        if (Boolean.FALSE.equals(attrs.get("pathResolved"))) {
            Object reason = attrs.get("pathIssueReason");
            warnings.add("Đường dẫn chưa giải được" + (reason == null ? "" : ": " + reason));
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
