package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.trace.Participant;
import com.ctv.flowtracer.ir.trace.Trace;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dò vết luồng CHỈ có giao diện: từ một phần tử, đi theo sự kiện → hàm xử lý → hàm gọi tiếp ({@code TRIGGERS}, {@code CALLS})
 * và các hiệu ứng trên giao diện ({@code OPENS} hộp thoại, {@code NAVIGATES} chuyển màn hình). Cho ra hai cột User ↔ Frontend:
 * bước người dùng thao tác và bước giao diện phản hồi (xem mock: "Bấm 'Thêm mới'" → "biểu mẫu thêm mới").
 *
 * <p>Nếu đi theo các hàm xử lý gặp một lời gọi HTTP ({@code SENDS}) thì đây không còn là luồng chỉ giao diện — cần nối với API
 * Backend (Giai đoạn 5) — nên ném {@link UnsupportedTraceStartException}. Hộp thoại được mở KHÔNG bị đi vào: thao tác trong
 * hộp thoại là một luồng khác, bắt đầu từ phần tử của hộp thoại đó.
 *
 * <p>Tất định: không đọc gì ngoài đồ thị con.
 */
final class UiOnlyTracer {

    /** {@code guard.branch} mà bộ phân tích ghi cho lời gọi nằm trong callback chạy khi hộp thoại đóng. */
    static final String AFTER_CLOSED = "afterClosed";

    static final String USER = "user";
    static final String FRONTEND = "fe";

    private static final Comparator<IrEdge> FLOW_ORDER =
            Comparator.comparing((IrEdge e) -> e.callOrder() == null ? Integer.MAX_VALUE : e.callOrder()).thenComparing(IrEdge::kind);

    Trace trace(Subgraph graph, TraceRequest request) {
        Map<String, IrNode> nodes = new HashMap<>();
        graph.nodes().forEach(n -> nodes.put(n.key(), n));
        Map<String, List<IrEdge>> out = new HashMap<>();
        for (IrEdge e : graph.edges()) {
            if (e.kind() == EdgeKind.TRIGGERS || e.kind() == EdgeKind.CALLS || e.kind() == EdgeKind.OPENS
                    || e.kind() == EdgeKind.NAVIGATES || e.kind() == EdgeKind.SENDS) {
                out.computeIfAbsent(e.fromKey(), k -> new ArrayList<>()).add(e);
            }
        }
        out.values().forEach(list -> list.sort(FLOW_ORDER));

        IrNode start = nodes.get(request.startKey());
        List<IrEdge> effects = new ArrayList<>();
        IrEdge firstTrigger = null;
        Set<String> visited = new HashSet<>();
        visited.add(start.key());
        // Duyệt theo thứ tự thực thi (DFS có thứ tự): hiệu ứng được ghi theo đúng thứ tự xảy ra
        walk(start.key(), out, nodes, visited, effects);
        if (!out.getOrDefault(start.key(), List.of()).isEmpty()) {
            firstTrigger = out.get(start.key()).get(0);
        }

        List<String> labels = new ArrayList<>();
        for (IrEdge effect : effects) {
            IrNode target = nodes.get(effect.toKey());
            if (target != null) {
                labels.add(effectLabel(effect.kind(), target));
            }
        }
        String reply = labels.isEmpty() ? "giao diện đã cập nhật" : String.join(", ", new LinkedHashSet<>(labels));
        EdgeKind replyKind = effects.isEmpty() ? EdgeKind.TRIGGERS : effects.get(effects.size() - 1).kind();

        var participants = List.of(
                new Participant(USER, "User", Participant.Role.USER, null),
                new Participant(FRONTEND, "Frontend", Participant.Role.FRONTEND, null));
        var steps = List.of(
                new TraceStep(1, USER, FRONTEND, EdgeKind.TRIGGERS, userAction(start), Confidence.CERTAIN, null,
                        firstTrigger == null ? null : graph.edgeRef(firstTrigger), false, false, false, null),
                new TraceStep(2, FRONTEND, USER, replyKind, reply, Confidence.CERTAIN, null, null, true, false, false, null));
        return new Trace(TraceIds.of(request), request.startKey(), participants, steps, List.of(), List.of(), true);
    }

    private void walk(
            String from, Map<String, List<IrEdge>> out, Map<String, IrNode> nodes, Set<String> visited, List<IrEdge> effects) {
        for (IrEdge edge : out.getOrDefault(from, List.of())) {
            if (edge.kind() == EdgeKind.SENDS) {
                throw new UnsupportedTraceStartException(
                        "Phần tử này gọi API Backend nên luồng cần nối Frontend với Backend (làm ở giai đoạn sau); "
                                + "hiện chỉ dò vết được phần tử không gọi máy chủ");
            }
            if (edge.confidence() == Confidence.UNRESOLVED || edge.toKey() == null) {
                continue;
            }
            if (edge.guard() != null && AFTER_CLOSED.equals(edge.guard().branch())) {
                continue; // chạy khi hộp thoại đóng (vd tải lại danh sách), không thuộc luồng của lần bấm
            }
            IrNode target = nodes.get(edge.toKey());
            if (target == null) {
                continue;
            }
            if (edge.kind() == EdgeKind.OPENS || edge.kind() == EdgeKind.NAVIGATES) {
                effects.add(edge); // hiệu ứng trên giao diện; không đi vào bên trong hộp thoại / màn hình đích
            } else if (visited.add(target.key())) {
                walk(target.key(), out, nodes, visited, effects);
            }
        }
    }

    /** Nhãn bước người dùng: `Bấm "Thêm mới"`; không có nhãn chữ thì dùng tên hàm xử lý. */
    private static String userAction(IrNode element) {
        Object event = element.attrs().get("event");
        String verb = switch (event == null ? "" : event.toString()) {
            case "click" -> "Bấm";
            case "ngSubmit" -> "Gửi biểu mẫu bằng";
            case "change", "selectionChange", "valueChange", "ngModelChange", "input" -> "Thay đổi";
            default -> "Thao tác với";
        };
        String name = element.displayName();
        if (name != null && !name.isBlank()) {
            return verb + " \"" + name + "\"";
        }
        Object control = element.attrs().get("control");
        return verb + " " + noun(element.attrs().get("elementType")) + " " + (control != null ? control : element.name());
    }

    /** Loại phần tử bằng tiếng Việt, đặt trước tên kỹ thuật khi phần tử không có chữ (`Bấm nút onSave`). */
    private static String noun(Object elementType) {
        return switch (elementType == null ? "" : elementType.toString()) {
            case "button" -> "nút";
            case "link" -> "liên kết";
            case "input", "textarea" -> "ô nhập";
            case "select" -> "ô chọn";
            case "checkbox", "radio", "toggle" -> "ô tích chọn";
            case "date" -> "ô chọn ngày";
            case "table" -> "bảng";
            case "pagination" -> "phân trang";
            default -> "phần tử";
        };
    }

    private static String effectLabel(EdgeKind kind, IrNode target) {
        String name = target.displayName() != null && !target.displayName().isBlank() ? target.displayName() : target.name();
        if (kind == EdgeKind.OPENS) {
            return "hộp thoại " + name;
        }
        return target.kind() == NodeKind.UI_SCREEN ? "màn hình " + name : name;
    }
}
