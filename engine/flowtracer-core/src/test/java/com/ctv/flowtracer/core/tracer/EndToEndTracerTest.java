package com.ctv.flowtracer.core.tracer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.Candidate;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.Guard;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import com.ctv.flowtracer.ir.trace.Participant;
import com.ctv.flowtracer.ir.trace.Trace;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Luồng End-to-End từ một phần tử giao diện gọi API: User → Frontend → Controller → Service → Repository → Bảng. */
class EndToEndTracerTest {

    private static final SourceLocation LOC = SourceLocation.of("x", 1);
    private static final String ENDPOINT = "java:endpoint:POST /api/v1/items";

    private final List<IrNode> nodes = new ArrayList<>();
    private final List<IrEdge> edges = new ArrayList<>();

    private IrNode node(String key, NodeKind kind, String name, String displayName, Map<String, Object> attrs) {
        var n = new IrNode(key, kind, name, displayName, LOC, attrs);
        nodes.add(n);
        return n;
    }

    private IrNode beMethod(String key, String layer, String className, String signature, String returnType) {
        return node(key, NodeKind.BE_METHOD, className + "." + signature, null, Map.of(
                "layer", layer, "className", className, "signature", signature, "businessName", "Sản phẩm",
                "returnType", returnType, "paramNames", List.of("request")));
    }

    private void edge(String from, String to, EdgeKind kind, int order) {
        edges.add(new IrEdge(from, to, kind, Confidence.CERTAIN, null, order, null, List.of(), LOC));
    }

    /** Nút Lưu → hàm xử lý → facade → lời gọi HTTP; lời gọi tải lại chạy khi hộp thoại đóng; phía BE một luồng đủ 3 tầng. */
    private void saveFlow(IrEdge link) {
        node("e:save", NodeKind.UI_ELEMENT, "onSave", "Lưu", Map.of("event", "click", "elementType", "button"));
        node("m:onSave", NodeKind.FE_METHOD, "Form.onSave", null, Map.of("layer", "COMPONENT", "signature", "onSave()"));
        // Lớp FE tên "...Service" vẫn thuộc cột Frontend, không phải cột Service của Backend
        node("m:create", NodeKind.FE_METHOD, "ItemService.create", null, Map.of("layer", "SERVICE", "signature", "create(input)"));
        node("m:reload", NodeKind.FE_METHOD, "ItemService.reload", null, Map.of("layer", "SERVICE", "signature", "reload()"));
        node("h:create", NodeKind.HTTP_CALL, "POST /gateway/api/v1/items", null, Map.of("httpMethod", "POST"));
        node("h:list", NodeKind.HTTP_CALL, "GET /gateway/api/v1/items", null, Map.of("httpMethod", "GET"));
        node(ENDPOINT, NodeKind.API_ENDPOINT, "POST /api/v1/items", null, Map.of("responseType", "ApiResponse<ItemResponse>"));
        beMethod("b:ctrl", "CONTROLLER", "com.x.ItemController", "create(ItemRequest)", "ApiResponse<ItemResponse>");
        beMethod("b:svc", "SERVICE", "com.x.ItemServiceImpl", "create(ItemRequest)", "ItemResponse");
        beMethod("b:repo", "REPOSITORY", "com.x.ItemRepository", "save(Item)", "Item");
        node("q:save", NodeKind.DATA_QUERY, "save", null, Map.of("verb", "INSERT/UPDATE", "operation", "WRITE"));
        node("t:item", NodeKind.DB_TABLE, "item", null, Map.of("name", "item"));

        edge("e:save", "m:onSave", EdgeKind.TRIGGERS, 0);
        edge("m:onSave", "m:create", EdgeKind.CALLS, 0);
        edges.add(new IrEdge("m:onSave", "m:reload", EdgeKind.CALLS, Confidence.CERTAIN, null, 1,
                new Guard(Guard.Type.IF, "sau khi hộp thoại đóng", "afterClosed"), List.of(), LOC));
        edge("m:create", "h:create", EdgeKind.SENDS, 0);
        edge("m:reload", "h:list", EdgeKind.SENDS, 0);
        edges.add(link);
        edge(ENDPOINT, "b:ctrl", EdgeKind.HANDLED_BY, 0);
        edge("b:ctrl", "b:svc", EdgeKind.CALLS, 0);
        edge("b:svc", "b:repo", EdgeKind.CALLS, 0);
        edge("b:repo", "q:save", EdgeKind.EXECUTES, 0);
        edge("q:save", "t:item", EdgeKind.WRITES, 0);
    }

    private Trace trace(List<OverrideRule> overrides) {
        var graph = new Subgraph(nodes, edges, Map.of(Subgraph.edgeId(edges.get(5)), "ref-link"), Set.of());
        return new Tracer().trace(graph, new TraceRequest("e:save", 12, "scan-1", List.of(), overrides));
    }

    private static List<String> lines(Trace trace) {
        return trace.steps().stream()
                .map(s -> s.index() + " " + s.from() + (s.returning() ? " --> " : " -> ") + s.to() + " " + s.label())
                .toList();
    }

    @Test
    void saveButtonGoesFromUserToDatabase() {
        saveFlow(new IrEdge("h:create", ENDPOINT, EdgeKind.HTTP_REQUEST, Confidence.INFERRED, "áp luật proxy", null, null, List.of(), LOC));

        Trace trace = trace(List.of());

        assertThat(trace.uiOnly()).isFalse();
        assertThat(trace.participants()).extracting(p -> p.id() + ":" + p.label()).containsExactly(
                "user:User", "fe:Frontend", "c1:Controller Sản phẩm", "s1:Service Sản phẩm", "r1:Repository Sản phẩm", "t1:Bảng item");
        assertThat(lines(trace)).containsExactly(
                "1 user -> fe Bấm \"Lưu\"",
                "2 fe -> c1 POST /api/v1/items",
                "3 c1 -> s1 create(request)",
                "4 s1 -> r1 save(request)",
                "5 r1 -> t1 INSERT/UPDATE",
                "6 t1 --> r1 OK",
                "7 r1 --> s1 Item",
                "8 s1 --> c1 ItemResponse",
                "9 c1 --> fe 200 ApiResponse<ItemResponse>",
                "10 fe --> user giao diện đã cập nhật");
        TraceStep http = trace.steps().get(1);
        assertThat(http.kind()).isEqualTo(EdgeKind.HTTP_REQUEST);
        assertThat(http.confidence()).isEqualTo(Confidence.INFERRED);
        assertThat(http.reason()).isEqualTo("áp luật proxy");
        assertThat(http.edgeRef()).isEqualTo("ref-link");
        assertThat(trace.warnings()).isEmpty();
    }

    @Test
    void unresolvedLinkIsNotGuessedButPinnedLinkIsFollowed() {
        saveFlow(new IrEdge("h:create", null, EdgeKind.HTTP_REQUEST, Confidence.UNRESOLVED, "2 API khớp", null, null,
                List.of(new Candidate(ENDPOINT, 0.9, "Cao"), new Candidate("java:endpoint:POST /api/v2/items", 0.9, "Cao")), LOC));

        Trace unresolved = trace(List.of());
        assertThat(unresolved.participants()).extracting(Participant::label).containsExactly("User", "Frontend", "API chưa xác định");
        assertThat(lines(unresolved)).containsExactly(
                "1 user -> fe Bấm \"Lưu\"",
                "2 fe -> x1 POST /gateway/api/v1/items",
                "3 x1 --> fe (chưa xác định)",
                "4 fe --> user giao diện đã cập nhật");
        assertThat(unresolved.steps().get(1).confidence()).isEqualTo(Confidence.UNRESOLVED);
        assertThat(unresolved.warnings()).anyMatch(w -> w.contains("xem bước 2"));

        Trace pinned = trace(List.of(new OverrideRule("h:create", ENDPOINT, EdgeKind.HTTP_REQUEST, OverrideRule.Decision.PIN)));
        TraceStep step = pinned.steps().get(1);
        assertThat(step.label()).isEqualTo("POST /api/v1/items");
        assertThat(step.pinned()).isTrue();
        assertThat(step.confidence()).isEqualTo(Confidence.CERTAIN);
        assertThat(step.reason()).isEqualTo("Chốt tay");
        assertThat(pinned.steps()).hasSize(10);
        assertThat(pinned.traceId()).isNotEqualTo(unresolved.traceId());
    }

    @Test
    void httpLinkCanBePinnedToAnApiOutsideTheCandidates() {
        saveFlow(new IrEdge("h:create", null, EdgeKind.HTTP_REQUEST, Confidence.UNRESOLVED, "Không có API nào khớp", null, null, List.of(), LOC));

        Trace pinned = trace(List.of(new OverrideRule("h:create", ENDPOINT, EdgeKind.HTTP_REQUEST, OverrideRule.Decision.PIN)));

        assertThat(lines(pinned)).element(1).isEqualTo("2 fe -> c1 POST /api/v1/items");
        assertThat(pinned.steps().get(1).pinned()).isTrue();
    }

    @Test
    void rejectedLinkFallsBackToUnresolved() {
        saveFlow(new IrEdge("h:create", ENDPOINT, EdgeKind.HTTP_REQUEST, Confidence.INFERRED, "suy luận", null, null, List.of(), LOC));

        Trace rejected = trace(List.of(new OverrideRule("h:create", ENDPOINT, EdgeKind.HTTP_REQUEST, OverrideRule.Decision.REJECT)));

        assertThat(rejected.participants()).extracting(Participant::label).contains("API chưa xác định");
        assertThat(rejected.steps().get(1).confidence()).isEqualTo(Confidence.UNRESOLVED);
    }

    @Test
    void externalSystemGetsItsOwnLane() {
        saveFlow(new IrEdge("h:create", "ext:system:pay.example.com", EdgeKind.HTTP_REQUEST, Confidence.INFERRED, "URL tuyệt đối", null,
                null, List.of(), LOC));
        node("ext:system:pay.example.com", NodeKind.EXTERNAL, "pay.example.com", "pay.example.com", Map.of());

        Trace trace = trace(List.of());

        assertThat(trace.participants()).extracting(p -> p.label() + ":" + p.role())
                .containsExactly("User:USER", "Frontend:FRONTEND", "pay.example.com:EXTERNAL");
        assertThat(lines(trace)).containsExactly(
                "1 user -> fe Bấm \"Lưu\"",
                "2 fe -> x1 POST /gateway/api/v1/items",
                "3 x1 --> fe kết quả",
                "4 fe --> user giao diện đã cập nhật");
    }

    @Test
    void severalEventsOfOneElementCallingTheSameHandlerAreTracedOnce() {
        saveFlow(new IrEdge("h:create", ENDPOINT, EdgeKind.HTTP_REQUEST, Confidence.INFERRED, "suy luận", null, null, List.of(), LOC));
        // cùng một hàm xử lý bị nối hai lần (vd hai sự kiện của một ô nhập): chỉ dò một lần
        edge("e:save", "m:onSave", EdgeKind.TRIGGERS, 1);

        Trace trace = trace(List.of());

        assertThat(trace.steps()).hasSize(10);
        assertThat(trace.steps().stream().filter(s -> s.kind() == EdgeKind.HTTP_REQUEST)).hasSize(1);
    }

    @Test
    void tryBlockIsFlattenedAndNotDrawnAsAnOptFrame() {
        nodes.clear();
        edges.clear();
        node(ENDPOINT, NodeKind.API_ENDPOINT, "POST /api/v1/items", null, Map.of("responseType", "ItemResponse"));
        beMethod("b:ctrl", "CONTROLLER", "com.x.ItemController", "create(ItemRequest)", "ItemResponse");
        beMethod("b:svc", "SERVICE", "com.x.ItemServiceImpl", "create(ItemRequest)", "ItemResponse");
        edge(ENDPOINT, "b:ctrl", EdgeKind.HANDLED_BY, 0);
        edges.add(new IrEdge("b:ctrl", "b:svc", EdgeKind.CALLS, Confidence.CERTAIN, null, 0, new Guard(Guard.Type.TRY, null, null), List.of(), LOC));

        Trace trace = new Tracer().trace(new Subgraph(nodes, edges), new TraceRequest(ENDPOINT, 12, "scan-1", List.of(), List.of()));

        assertThat(trace.blocks()).isEmpty();
        assertThat(lines(trace)).contains("2 c1 -> s1 create(request)");
    }
}
