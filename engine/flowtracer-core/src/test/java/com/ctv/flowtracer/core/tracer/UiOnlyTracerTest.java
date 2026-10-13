package com.ctv.flowtracer.core.tracer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.Candidate;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import com.ctv.flowtracer.ir.trace.Participant;
import com.ctv.flowtracer.ir.trace.Trace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Dò vết luồng chỉ có giao diện: User ↔ Frontend, kèm các trường hợp phải từ chối. */
class UiOnlyTracerTest {

    private static final SourceLocation LOC = SourceLocation.of("a.html", 1);

    private final Tracer tracer = new Tracer();
    private final List<IrNode> nodes = new ArrayList<>();
    private final List<IrEdge> edges = new ArrayList<>();

    private IrNode node(String key, NodeKind kind, String name, String displayName, Map<String, Object> attrs) {
        var n = new IrNode(key, kind, name, displayName, LOC, attrs);
        nodes.add(n);
        return n;
    }

    private IrNode element(String key, String label, String event) {
        return node(key, NodeKind.UI_ELEMENT, "handler", label, Map.of("event", event, "elementType", "button"));
    }

    private void edge(IrNode from, IrNode to, EdgeKind kind, int order) {
        edges.add(new IrEdge(from.key(), to.key(), kind, Confidence.CERTAIN, null, order, null, List.of(), LOC));
    }

    private Trace trace(IrNode start) {
        var graph = new Subgraph(nodes, edges, Map.of(), java.util.Set.of());
        return tracer.trace(graph, new TraceRequest(start.key(), 12, "scan-1", List.of(), List.of()));
    }

    @Test
    void buttonThatOpensDialogGivesTwoStepsBetweenUserAndFrontend() {
        IrNode button = element("e:create", "Thêm mới", "click");
        IrNode method = node("m:open", NodeKind.FE_METHOD, "List.openCreate", null, Map.of());
        IrNode dialog = node("d:form", NodeKind.UI_DIALOG, "FormDialog", "Thêm danh mục", Map.of());
        edge(button, method, EdgeKind.TRIGGERS, 0);
        edge(method, dialog, EdgeKind.OPENS, 0);

        Trace trace = trace(button);

        assertThat(trace.uiOnly()).isTrue();
        assertThat(trace.participants()).extracting(Participant::label).containsExactly("User", "Frontend");
        assertThat(trace.steps()).hasSize(2);
        assertThat(trace.steps().get(0).label()).isEqualTo("Bấm \"Thêm mới\"");
        assertThat(trace.steps().get(0).returning()).isFalse();
        assertThat(trace.steps().get(1).label()).isEqualTo("hộp thoại Thêm danh mục");
        assertThat(trace.steps().get(1).returning()).isTrue();
        assertThat(trace.warnings()).isEmpty();
    }

    @Test
    void effectsAreListedInExecutionOrder() {
        IrNode button = element("e:go", "Xem", "click");
        IrNode method = node("m:go", NodeKind.FE_METHOD, "List.go", null, Map.of());
        IrNode screen = node("s:orders", NodeKind.UI_SCREEN, "OrderList", "Đơn hàng", Map.of());
        IrNode dialog = node("d:note", NodeKind.UI_DIALOG, "Note", null, Map.of());
        edge(button, method, EdgeKind.TRIGGERS, 0);
        edge(method, screen, EdgeKind.NAVIGATES, 1);
        edge(method, dialog, EdgeKind.OPENS, 0);

        assertThat(trace(button).steps().get(1).label()).isEqualTo("hộp thoại Note, màn hình Đơn hàng");
    }

    @Test
    void stateOnlyButtonRepliesThatTheScreenIsUpdated() {
        IrNode button = element("e:toggle", "Bộ lọc", "click");

        Trace trace = trace(button);

        assertThat(trace.steps()).hasSize(2);
        assertThat(trace.steps().get(1).label()).isEqualTo("giao diện đã cập nhật");
    }

    @Test
    void routerLinkNavigatesStraightFromTheElement() {
        IrNode link = node("e:link", NodeKind.UI_ELEMENT, "a", "Đơn hàng", Map.of("elementType", "link"));
        IrNode screen = node("s:orders", NodeKind.UI_SCREEN, "OrderList", "Đơn hàng", Map.of());
        edge(link, screen, EdgeKind.NAVIGATES, 0);

        Trace trace = trace(link);

        assertThat(trace.steps().get(0).label()).isEqualTo("Thao tác với \"Đơn hàng\"");
        assertThat(trace.steps().get(1).label()).isEqualTo("màn hình Đơn hàng");
    }

    @Test
    void elementWithoutLabelIsNamedByItsTypeAndHandler() {
        IrNode button = node("e:x", NodeKind.UI_ELEMENT, "onSave", null, Map.of("event", "click", "elementType", "button"));

        assertThat(trace(button).steps().get(0).label()).isEqualTo("Bấm nút onSave");
    }

    @Test
    void flowThatSendsHttpIsNotUiOnly() {
        IrNode button = element("e:delete", "Xóa", "click");
        IrNode handler = node("m:onDelete", NodeKind.FE_METHOD, "List.onDelete", null, Map.of());
        IrNode facade = node("m:remove", NodeKind.FE_METHOD, "Facade.remove", null, Map.of());
        IrNode http = node("h:delete", NodeKind.HTTP_CALL, "DELETE /x", null, Map.of());
        edge(button, handler, EdgeKind.TRIGGERS, 0);
        edge(handler, facade, EdgeKind.CALLS, 0);
        edge(facade, http, EdgeKind.SENDS, 0);

        assertThat(UiOnlyTracer.callsServer(new Subgraph(nodes, edges), button.key())).isTrue();
        assertThatThrownBy(() -> new UiOnlyTracer().trace(
                        new Subgraph(nodes, edges), new TraceRequest(button.key(), 12, "scan-1", List.of(), List.of())))
                .isInstanceOf(UnsupportedTraceStartException.class);
        // Tracer chuyển sang luồng End-to-End (chưa nối được API nên tới cột "API chưa xác định")
        Trace trace = trace(button);
        assertThat(trace.uiOnly()).isFalse();
        assertThat(trace.participants()).extracting(Participant::label).containsExactly("User", "Frontend", "API chưa xác định");
    }

    @Test
    void unresolvedAndDialogContentsAreNotFollowed() {
        IrNode button = element("e:open", "Mở", "click");
        IrNode method = node("m:open", NodeKind.FE_METHOD, "List.open", null, Map.of());
        IrNode dialog = node("d:form", NodeKind.UI_DIALOG, "Form", "Biểu mẫu", Map.of());
        IrNode inner = node("m:save", NodeKind.FE_METHOD, "Form.save", null, Map.of());
        IrNode http = node("h:save", NodeKind.HTTP_CALL, "POST /x", null, Map.of());
        edge(button, method, EdgeKind.TRIGGERS, 0);
        edge(method, dialog, EdgeKind.OPENS, 0);
        edge(dialog, inner, EdgeKind.TRIGGERS, 0); // không xảy ra trong IR thật; chỉ để chứng minh không đi vào hộp thoại
        edge(inner, http, EdgeKind.SENDS, 0);
        edges.add(new IrEdge(method.key(), null, EdgeKind.CALLS, Confidence.UNRESOLVED, "nhiều lớp hiện thực", 1, null,
                List.of(new Candidate(inner.key(), 1, "ứng viên")), LOC));

        Trace trace = trace(button);

        assertThat(trace.steps().get(1).label()).isEqualTo("hộp thoại Biểu mẫu");
    }

    @Test
    void sameGraphGivesSameTraceId() {
        IrNode button = element("e:create", "Thêm mới", "click");
        assertThat(trace(button).traceId()).isEqualTo(trace(button).traceId());
    }
}
