package com.ctv.flowtracer.app.explanation.service;

import com.ctv.flowtracer.app.ai.model.AiCall;
import com.ctv.flowtracer.app.ai.model.AiOutcome;
import com.ctv.flowtracer.app.ai.model.AiPurpose;
import com.ctv.flowtracer.app.ai.model.AiRejectedException;
import com.ctv.flowtracer.app.ai.service.AiGateway;
import com.ctv.flowtracer.app.ai.service.PromptTemplates;
import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.service.GraphQueryService;
import com.ctv.flowtracer.core.explain.ElementNameRule;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Mục đích AI {@code ELEMENT_NAMING}: đặt tên chuẩn tiếng Việt cho các phần tử tương tác của một màn hình / hộp thoại từ chữ trên giao
 * diện, tên điều khiển, tên hàm xử lý, màn hình chứa và API mà phần tử gọi (method, đường dẫn, mô tả, Controller + hàm). Chỉ đổi
 * CÁCH HIỂN THỊ tên: không tạo / xóa thành phần, không đổi liên kết hay diagram. Tên không đạt {@link ElementNameRule} bị bỏ (giữ tên
 * lấy từ mã nguồn).
 */
@Service
public class ElementNamingService {

    /** Số phần tử mỗi lần gọi AI (màn hình lớn được chia nhiều lần). */
    static final int CHUNK = 30;

    /** Chữ trên giao diện quá chung để hiểu nghĩa nếu không có ngữ cảnh (chữ thường). */
    private static final Set<String> GENERIC = Set.of("lưu", "xóa", "sửa", "thêm", "thêm mới", "ok", "hủy", "đóng", "đồng ý", "xác nhận",
            "tìm kiếm", "chọn", "cập nhật", "tạo mới", "save", "delete", "edit", "add", "cancel", "close", "submit", "search", "...", "…");

    private final AiGateway gateway;
    private final PromptTemplates prompts;
    private final GraphQueryService graphQueries;
    private final ExplanationService explanations;
    private final ElementNameRule rule = new ElementNameRule();

    public ElementNamingService(AiGateway gateway, PromptTemplates prompts, GraphQueryService graphQueries, ExplanationService explanations) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.graphQueries = graphQueries;
        this.explanations = explanations;
    }

    /** Kết quả đặt tên cho một thành phần chứa. */
    public record Result(AiOutcome.Status status, String message, String provider, String model, int named, int total) {}

    /**
     * Đặt tên cho mọi phần tử trong {@code containerKey}.
     *
     * @param force true = đặt lại kể cả khi đã có bản lưu
     * @throws AppException {@code NODE_NOT_FOUND}; {@code EXPLANATION_UNSUPPORTED} nếu không phải thành phần chứa
     */
    public Result name(UUID workspaceId, String containerKey, boolean force) {
        IrNodeRow container = graphQueries.requireNode(workspaceId, containerKey);
        if (container.node().kind() == NodeKind.UI_ELEMENT || !container.node().kind().name().startsWith("UI_")) {
            throw new AppException(ErrorCode.EXPLANATION_UNSUPPORTED, "Chỉ đặt tên được cho phần tử bên trong màn hình, hộp thoại, tab hoặc form");
        }
        List<Candidate> elements = worthNaming(workspaceId, graphQueries.elementsInside(workspaceId, container));
        if (elements.isEmpty()) {
            return new Result(AiOutcome.Status.CACHED, "Không có phần tử nào cần đặt tên lại: tên lấy từ mã nguồn đã đủ rõ.", null, null, 0, 0);
        }
        String containerName = label(container.node());
        int named = 0;
        AiOutcome first = null;
        AiOutcome failure = null;
        for (int from = 0, chunk = 0; from < elements.size(); from += CHUNK, chunk++) {
            List<Candidate> part = elements.subList(from, Math.min(elements.size(), from + CHUNK));
            AiOutcome outcome = gateway.execute(call(workspaceId, containerKey, containerName, chunk, part), force);
            if (outcome.hasContent()) {
                named += size(outcome);
                first = first == null ? outcome : first;
            } else {
                failure = failure == null ? outcome : failure;
                if (outcome.status() != AiOutcome.Status.REJECTED) {
                    break; // tắt / hết hạn mức / provider lỗi: các lần sau cũng vậy
                }
            }
        }
        if (first == null) {
            return new Result(failure.status(), failure.message(), null, null, 0, elements.size());
        }
        return new Result(first.status(), failure == null ? null : failure.message(), first.provider(), first.model(), named, elements.size());
    }

    /** Phần tử kèm dữ kiện luồng của nó (tính một lần). */
    private record Candidate(IrNodeRow row, ExplanationService.ElementFlow flow, boolean important) {}

    /**
     * Chỉ giữ phần tử THỰC SỰ cần AI: có gọi API / mở màn hình / có lời gọi chưa xác định (tên cần ngữ cảnh), chưa có chữ trên giao
     * diện, hoặc chữ quá chung ("Lưu", "Xóa", "OK"…). Phần tử có chữ rõ nghĩa, không gọi gì thì giữ tên từ mã nguồn — đỡ tốn lượt gọi.
     * Phần tử có API / mở màn hình xếp trước nên màn hình lớn vẫn được đặt tên những nút quan trọng nhất ở lần gọi đầu.
     */
    private List<Candidate> worthNaming(UUID workspaceId, List<IrNodeRow> all) {
        var important = new ArrayList<Candidate>();
        var others = new ArrayList<Candidate>();
        for (IrNodeRow row : all) {
            ExplanationService.ElementFlow flow = explanations.elementFlow(workspaceId, row);
            boolean important1 = !flow.apis().isEmpty() || !flow.opens().isEmpty() || !flow.navigates().isEmpty() || !flow.unresolved().isEmpty();
            String text = row.node().displayName();
            if (important1) {
                important.add(new Candidate(row, flow, true));
            } else if (text == null || text.isBlank() || GENERIC.contains(text.strip().toLowerCase(java.util.Locale.ROOT))) {
                others.add(new Candidate(row, flow, false));
            }
        }
        important.addAll(others);
        return important;
    }

    private AiCall call(UUID workspaceId, String containerKey, String containerName, int chunk, List<Candidate> part) {
        Map<String, String> idToKey = new LinkedHashMap<>();
        var lines = new StringBuilder();
        for (int i = 0; i < part.size(); i++) {
            String id = "e" + (i + 1);
            idToKey.put(id, part.get(i).row().node().key());
            lines.append(id).append(" | ").append(describe(part.get(i))).append('\n');
        }
        var rendered = prompts.render("element-naming.v1", Map.of("container", containerName, "elements", lines.toString()));
        String target = chunk == 0 ? containerKey : containerKey + "#" + chunk;
        return new AiCall(workspaceId, AiPurpose.ELEMENT_NAMING, target, rendered.version(), rendered.system(), rendered.user(),
                rendered.schema(), 2500, raw -> validate(raw, idToKey));
    }

    /** Một dòng dữ kiện của phần tử: loại, chữ trên giao diện, control, hàm xử lý, khóa dịch, route, API gọi, nơi mở. */
    private String describe(Candidate candidate) {
        IrNode n = candidate.row().node();
        Map<String, Object> a = n.attrs();
        var parts = new ArrayList<String>();
        parts.add("loại: " + text(a.get("elementType"), "phần tử"));
        parts.add("chữ trên giao diện: " + (n.displayName() == null ? "(không có)" : "\"" + n.displayName() + "\""));
        add(parts, "tên control", a.get("control"));
        if (a.get("handlers") instanceof List<?> handlers && !handlers.isEmpty()) {
            parts.add("hàm xử lý: " + handlers.stream().limit(3).map(String::valueOf).collect(Collectors.joining(", ")));
        }
        add(parts, "khóa dịch", a.get("i18nKey"));
        add(parts, "đường dẫn chuyển tới", a.get("routerLink"));
        add(parts, "thẻ", a.get("tag"));
        ExplanationService.ElementFlow flow = candidate.flow();
        if (!flow.apis().isEmpty()) {
            parts.add("gọi API: " + flow.apis().stream().limit(3).map(ElementNamingService::api).collect(Collectors.joining("; ")));
        }
        if (!flow.unresolved().isEmpty()) {
            parts.add("có lời gọi máy chủ chưa xác định API đích: " + flow.unresolved().stream().limit(2).map(IrNode::name).collect(Collectors.joining("; ")));
        }
        Set<String> opens = new LinkedHashSet<>();
        flow.opens().forEach(o -> opens.add("hộp thoại " + label(o)));
        flow.navigates().forEach(o -> opens.add("màn hình " + label(o)));
        if (!opens.isEmpty()) {
            parts.add("mở: " + String.join(", ", opens));
        }
        return String.join(" | ", parts);
    }

    /** "PUT /api/v1/authors/{id} (Cập nhật tác giả; AuthorController#update)". */
    private static String api(IrNode api) {
        Map<String, Object> a = api.attrs();
        var extra = new ArrayList<String>();
        if (api.displayName() != null) {
            extra.add(api.displayName());
        }
        if (a.get("handler") instanceof String handler) {
            extra.add(handler.replaceAll("\\(.*\\)", ""));
        }
        return api.name() + (extra.isEmpty() ? "" : " (" + String.join("; ", extra) + ")");
    }

    private Map<String, Object> validate(Map<String, Object> raw, Map<String, String> idToKey) {
        var names = new ArrayList<Map<String, Object>>();
        if (raw.get("names") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("id") instanceof String id && idToKey.containsKey(id)) {
                    String label = rule.clean(m.get("label") instanceof String s ? s : null);
                    if (label != null) {
                        names.add(Map.of("nodeKey", idToKey.get(id), "label", label));
                    }
                }
            }
        }
        if (names.isEmpty()) {
            throw new AiRejectedException("Không có tên hợp lệ nào");
        }
        return Map.of("names", names);
    }

    private static int size(AiOutcome outcome) {
        return outcome.content().get("names") instanceof List<?> l ? l.size() : 0;
    }

    private static void add(List<String> parts, String name, Object value) {
        if (value instanceof String s && !s.isBlank()) {
            parts.add(name + ": " + s);
        }
    }

    private static String text(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }

    private static String label(IrNode n) {
        return n.displayName() != null && !n.displayName().isBlank() ? n.displayName() : n.name();
    }
}
