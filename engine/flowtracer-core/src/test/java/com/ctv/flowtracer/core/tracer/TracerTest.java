package com.ctv.flowtracer.core.tracer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.Candidate;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.Guard;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.NoiseFilter;
import com.ctv.flowtracer.ir.SourceLocation;
import com.ctv.flowtracer.ir.trace.Trace;
import com.ctv.flowtracer.ir.trace.TraceBlock;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Dò vết trên đồ thị dựng tay: từng quy tắc dựng bước, khối, lọc nhiễu, vòng lặp và liên kết chốt tay. */
class TracerTest {

    private static final SourceLocation LOC = SourceLocation.of("A.java", 1);

    private final Tracer tracer = new Tracer();
    private final List<IrNode> nodes = new ArrayList<>();
    private final List<IrEdge> edges = new ArrayList<>();

    // ---------------- dựng đồ thị ----------------

    private IrNode endpoint(String name, String responseType) {
        Map<String, Object> attrs = responseType == null ? Map.of() : Map.of("responseType", responseType);
        var n = new IrNode("ep:" + name, NodeKind.API_ENDPOINT, name, null, LOC, attrs);
        nodes.add(n);
        return n;
    }

    private IrNode method(String cls, String name, String layer, Map<String, Object> extra) {
        var attrs = new java.util.TreeMap<String, Object>(extra);
        attrs.put("layer", layer);
        attrs.put("className", "p." + cls);
        attrs.put("signature", name + "()");
        attrs.putIfAbsent("businessName", cls.replaceAll("(Controller|Service|Repository)$", ""));
        var n = new IrNode("m:" + cls + "#" + name, NodeKind.BE_METHOD, cls + "." + name, null, LOC, attrs);
        nodes.add(n);
        return n;
    }

    private IrNode method(String cls, String name, String layer) {
        return method(cls, name, layer, Map.of());
    }

    private void edge(String from, String to, EdgeKind kind, int order, Guard guard) {
        edges.add(new IrEdge(from, to, kind, Confidence.CERTAIN, null, order, guard, List.of(), LOC));
    }

    private void calls(IrNode from, IrNode to, int order) {
        edge(from.key(), to.key(), EdgeKind.CALLS, order, null);
    }

    private IrNode query(IrNode from, String table, String verb, int order) {
        var q = new IrNode("q:" + from.key() + order, NodeKind.DATA_QUERY, verb + " " + table, null, LOC, Map.of("verb", verb));
        var t = new IrNode("t:" + table, NodeKind.DB_TABLE, table, null, null, Map.of("reason", "thử"));
        nodes.add(q);
        if (nodes.stream().noneMatch(n -> n.key().equals(t.key()))) {
            nodes.add(t);
        }
        edge(from.key(), q.key(), EdgeKind.EXECUTES, order, null);
        edges.add(new IrEdge(q.key(), t.key(), verb.equals("SELECT") ? EdgeKind.READS : EdgeKind.WRITES, Confidence.CERTAIN, null, null, null, List.of(), LOC));
        return q;
    }

    private Trace trace(IrNode start) {
        return trace(start, TraceRequest.DEFAULT_MAX_DEPTH, List.of(), List.of());
    }

    private Trace trace(IrNode start, int depth, List<NoiseFilter> filters, List<OverrideRule> overrides) {
        return tracer.trace(new Subgraph(nodes, edges), new TraceRequest(start.key(), depth, "run-1", filters, overrides));
    }

    private IrNode controllerFlow(IrNode ep, IrNode handler) {
        edge(ep.key(), handler.key(), EdgeKind.HANDLED_BY, 0, null);
        return handler;
    }

    private static List<String> labels(Trace t) {
        return t.steps().stream().map(s -> s.index() + (s.returning() ? "<" : ">") + s.label()).toList();
    }

    // ---------------- ca kiểm thử ----------------

    @Test
    void simpleFlowHasClientControllerServiceRepositoryDatabaseWithReturns() {
        IrNode ep = endpoint("GET /a", "Page<A>");
        IrNode ctl = controllerFlow(ep, method("AController", "list", "CONTROLLER"));
        IrNode svc = method("AService", "list", "SERVICE", Map.of("returnType", "Page<A>", "paramNames", List.of("q")));
        IrNode repo = method("ARepository", "findAll", "REPOSITORY", Map.of("returnType", "java.util.List<p.A>"));
        calls(ctl, svc, 0);
        calls(svc, repo, 0);
        query(repo, "a", "SELECT", 0);

        Trace t = trace(ep);

        assertThat(t.participants()).extracting(p -> p.id() + ":" + p.label())
                .containsExactly("user:Client", "c1:Controller A", "s1:Service A", "r1:Repository A", "t1:Bảng a");
        assertThat(labels(t)).containsExactly(
                "1>GET /a", "2>list(q)", "3>findAll()", "4>SELECT", "5<kết quả", "6<List<A>", "7<Page<A>", "8<200 Page<A>");
        assertThat(t.blocks()).isEmpty();
        assertThat(t.warnings()).isEmpty();
        assertThat(t.traceId()).hasSize(32);
    }

    @Test
    void sameClassHelpersAndEntityMethodsAreSelfStepsButUtilityClassesAreHiddenYetStillDescended() {
        IrNode ep = endpoint("POST /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "create", "CONTROLLER"));
        IrNode svc = method("AService", "create", "SERVICE");
        IrNode helper = method("AService", "check", "SERVICE", Map.of("private", true));
        IrNode util = method("Dates", "today", "OTHER");
        IrNode repo = method("ARepository", "save", "REPOSITORY");
        calls(ctl, svc, 0);
        calls(svc, helper, 0);
        calls(helper, util, 0);
        calls(helper, repo, 1); // lời gọi thật sang cột khác nằm TRONG hàm private vẫn phải hiện ra
        calls(svc, util, 1);

        Trace t = trace(ep);

        assertThat(labels(t)).containsExactly(
                "1>POST /a", "2>create()", "3>check()", "4>save()", "5<kết quả", "6<kết quả", "7<200 OK");
        assertThat(t.steps().get(2).isSelf()).isTrue();
        assertThat(t.steps().get(2).from()).isEqualTo("s1");
    }

    @Test
    void noiseFiltersDropTheNodeAndItsSubtree() {
        IrNode ep = endpoint("GET /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "get", "CONTROLLER"));
        IrNode svc = method("AService", "get", "SERVICE");
        IrNode mapper = method("AMapper", "toDto", "OTHER");
        IrNode inner = method("ARepository", "hidden", "REPOSITORY");
        calls(ctl, svc, 0);
        calls(svc, mapper, 0);
        calls(mapper, inner, 0);

        Trace t = trace(ep, 12, List.of(new NoiseFilter("*Mapper#*", "mapper")), List.of());

        assertThat(labels(t)).containsExactly("1>GET /a", "2>get()", "3<kết quả", "4<200 OK");
        assertThat(t.participants()).noneMatch(p -> p.label().contains("Repository"));
    }

    @Test
    void guardClauseThrowBecomesAltWhoseElseRunsToTheEndOfTheTrace() {
        IrNode ep = endpoint("POST /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "create", "CONTROLLER"));
        Map<String, Object> thrown = Map.of("throws", List.of(Map.of(
                "order", 1, "exception", "BusinessException", "messageKey", "A_EXISTS",
                "guardType", "IF", "guardText", "repo.exists()", "guardBranch", "then", "line", 9)));
        IrNode svc = method("AService", "create", "SERVICE", thrown);
        IrNode repo = method("ARepository", "exists", "REPOSITORY");
        IrNode save = method("ARepository", "save", "REPOSITORY");
        calls(ctl, svc, 0);
        calls(svc, repo, 0);
        calls(svc, save, 2);

        Trace t = trace(ep);

        assertThat(labels(t)).containsExactly(
                "1>POST /a", "2>create()", "3>exists()", "4<kết quả", "5<A_EXISTS", "6<BusinessException", "7>save()", "8<kết quả", "9<kết quả", "10<200 OK");
        assertThat(t.steps().get(4).error()).isTrue();
        assertThat(t.steps().get(5).error()).isTrue();
        assertThat(t.blocks()).singleElement().satisfies(b -> {
            assertThat(b.type()).isEqualTo(TraceBlock.Type.ALT);
            assertThat(b.label()).isEqualTo("repo.exists()");
            assertThat(b.fromStep()).isEqualTo(5);
            assertThat(b.toStep()).isEqualTo(10);
            assertThat(b.elseBranches()).singleElement().satisfies(e -> assertThat(e.fromStep()).isEqualTo(7));
        });
    }

    @Test
    void inputValidationThrowsAreNotDrawnButDataDependentOnesAre() {
        IrNode ep = endpoint("POST /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "create", "CONTROLLER"));
        // order: 0 gọi exists (cột khác) · 1 throw phụ thuộc exists · 2 throw kiểm tra đầu vào độc lập
        Map<String, Object> thrown = Map.of("throws", List.of(
                Map.of("order", 1, "exception", "BusinessException", "messageKey", "DUPLICATE",
                        "guardType", "IF", "guardText", "repo.exists(code)", "guardBranch", "then", "line", 5),
                Map.of("order", 2, "exception", "BusinessException", "messageKey", "NAME_REQUIRED",
                        "guardType", "IF", "guardText", "name == null", "guardBranch", "then", "line", 7)));
        IrNode svc = method("AService", "create", "SERVICE", thrown);
        IrNode repo = method("ARepository", "exists", "REPOSITORY");
        calls(ctl, svc, 0);
        calls(svc, repo, 0);

        Trace t = trace(ep);

        assertThat(labels(t)).contains("5<DUPLICATE").doesNotContain("NAME_REQUIRED");
        assertThat(t.steps()).extracting(TraceStep::label).doesNotContain("NAME_REQUIRED");
        assertThat(t.blocks()).singleElement().satisfies(b -> assertThat(b.label()).isEqualTo("repo.exists(code)"));
    }

    @Test
    void entityMethodsAreSelfStepsAndValidationExceptionsAreNeverDrawn() {
        IrNode ep = endpoint("POST /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "create", "CONTROLLER"));
        Map<String, Object> thrown = Map.of("throws", List.of(Map.of(
                "order", 2, "exception", "ValidationException", "messageKey", "BAD",
                "guardType", "IF", "guardText", "repo.exists(code)", "guardBranch", "then", "line", 5)));
        IrNode svc = method("AService", "create", "SERVICE", thrown);
        IrNode repo = method("ARepository", "exists", "REPOSITORY");
        IrNode entity = method("Order", "markDeleted", "OTHER", Map.of("domainModel", true));
        calls(ctl, svc, 0);
        calls(svc, repo, 0);
        calls(svc, entity, 1);

        Trace t = trace(ep);

        assertThat(labels(t)).containsExactly(
                "1>POST /a", "2>create()", "3>exists()", "4<kết quả", "5>markDeleted()", "6<kết quả", "7<200 OK");
        assertThat(t.blocks()).isEmpty();
    }

    @Test
    void repeatedCallsAreNotExpandedAgainAndPointToTheFirstCall() {
        IrNode ep = endpoint("POST /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "create", "CONTROLLER"));
        IrNode svc = method("AService", "create", "SERVICE");
        IrNode repo = method("ARepository", "find", "REPOSITORY");
        calls(ctl, svc, 0);
        for (int i = 0; i < 30; i++) {
            calls(svc, repo, i); // cùng một hàm được gọi 30 lần: 30 × 4 = 120 bước nếu vẽ đủ
        }
        query(repo, "t", "SELECT", 0);

        Trace t = trace(ep);

        assertThat(t.steps().size()).isLessThanOrEqualTo(Tracer.MAX_STEPS);
        assertThat(t.warnings().getFirst()).startsWith("Luồng đầy đủ có ").contains("không vẽ lại phần bên trong");
        // lần gọi đầu có đủ phần bên trong (SELECT t); các lần sau chỉ có bước đi + trả về và trỏ về bước 3
        assertThat(t.steps()).filteredOn(s -> s.label().equals("SELECT")).hasSize(1);
        assertThat(t.steps()).filteredOn(s -> s.repeatOf() != null).hasSize(29).allMatch(s -> s.repeatOf() == 3);
    }

    @Test
    void neverCutsBusinessCallsEvenWhenTheFlowStaysLongerThanTheLimit() {
        IrNode ep = endpoint("POST /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "create", "CONTROLLER"));
        Map<String, Object> thrown = Map.of("throws", List.of(Map.of(
                "order", 1, "exception", "BusinessException", "messageKey", "DUP",
                "guardType", "IF", "guardText", "repo.find0()", "guardBranch", "then", "line", 5)));
        IrNode svc = method("AService", "create", "SERVICE", thrown);
        calls(ctl, svc, 0);
        for (int i = 0; i < 40; i++) {
            IrNode repo = method("ARepository", "find" + i, "REPOSITORY"); // 40 hàm KHÁC nhau: không gộp được
            calls(svc, repo, i == 0 ? 0 : i + 1);
            query(repo, "t" + (i % 3), "SELECT", 0);
        }

        Trace t = trace(ep);

        assertThat(t.steps().stream().filter(s -> !s.returning() && !s.isSelf() && s.label().startsWith("find")).count()).isEqualTo(40);
        assertThat(t.steps().size()).isGreaterThan(Tracer.MAX_STEPS);
        assertThat(t.warnings().getFirst()).contains("bỏ các bước tự xử lý").contains("vẫn dài " + t.steps().size() + " bước");
        // nhánh lỗi nghiệp vụ được giữ
        assertThat(t.steps()).anyMatch(TraceStep::error);
        assertThat(t.blocks()).isNotEmpty();
    }

    @Test
    void everyTableGetsItsOwnColumnAndUnknownTablesShareADatabaseColumn() {
        IrNode ep = endpoint("GET /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "get", "CONTROLLER"));
        IrNode svc = method("AService", "get", "SERVICE");
        IrNode repo = method("ARepository", "join", "REPOSITORY");
        IrNode blind = method("ARepository", "blind", "REPOSITORY");
        calls(ctl, svc, 0);
        calls(svc, repo, 0);
        calls(svc, blind, 1);
        // một truy vấn đụng 2 bảng
        IrNode q = query(repo, "orders", "SELECT", 0);
        var items = new IrNode("t:items", NodeKind.DB_TABLE, "items", null, null, Map.of("reason", "thử"));
        nodes.add(items);
        edges.add(new IrEdge(q.key(), items.key(), EdgeKind.READS, Confidence.INFERRED, "suy ra", null, null, List.of(), LOC));
        // một truy vấn không biết bảng
        var unknown = new IrNode("q:blind", NodeKind.DATA_QUERY, "SELECT", null, LOC, Map.of("verb", "SELECT"));
        nodes.add(unknown);
        edge(blind.key(), unknown.key(), EdgeKind.EXECUTES, 0, null);

        Trace t = trace(ep);

        assertThat(t.participants()).extracting(p -> p.id() + ":" + p.label()).containsExactly(
                "user:Client", "c1:Controller A", "s1:Service A", "r1:Repository A", "t1:Bảng items", "t2:Bảng orders", "db:Database");
        assertThat(t.steps()).filteredOn(s -> !s.returning() && s.label().startsWith("SELECT")).extracting(s -> s.to() + ":" + s.confidence())
                .containsExactly("t1:INFERRED", "t2:CERTAIN", "db:CERTAIN");
        assertThat(t.participants().get(4).nodeKey()).isEqualTo("t:items");
        assertThat(t.warnings()).anyMatch(w -> w.contains("truy vấn chưa xác định được bảng"));
    }

    @Test
    void unconditionalThrowAbortsTheRestOfTheFlow() {
        IrNode ep = endpoint("GET /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "get", "CONTROLLER"));
        Map<String, Object> thrown = Map.of("throws", List.of(Map.of("order", 0, "exception", "IllegalStateException", "line", 3)));
        IrNode svc = method("AService", "get", "SERVICE", thrown);
        IrNode repo = method("ARepository", "never", "REPOSITORY");
        calls(ctl, svc, 0);
        calls(svc, repo, 1);

        Trace t = trace(ep);

        assertThat(labels(t)).containsExactly("1>GET /a", "2>get()", "3<IllegalStateException", "4<IllegalStateException");
        assertThat(t.blocks()).isEmpty();
    }

    @Test
    void optionalLoopAndExplicitElseGuardsBecomeBlocks() {
        IrNode ep = endpoint("POST /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "run", "CONTROLLER"));
        IrNode svc = method("AService", "run", "SERVICE");
        IrNode r1 = method("ARepository", "one", "REPOSITORY");
        IrNode r2 = method("ARepository", "two", "REPOSITORY");
        IrNode r3 = method("ARepository", "three", "REPOSITORY");
        IrNode r4 = method("ARepository", "four", "REPOSITORY");
        calls(ctl, svc, 0);
        edge(svc.key(), r1.key(), EdgeKind.CALLS, 0, new Guard(Guard.Type.IF, "x > 0", "then"));
        edge(svc.key(), r2.key(), EdgeKind.CALLS, 1, new Guard(Guard.Type.ELSE, "x > 0", "else"));
        edge(svc.key(), r3.key(), EdgeKind.CALLS, 2, new Guard(Guard.Type.LOOP, "items", "loop"));
        edge(svc.key(), r4.key(), EdgeKind.CALLS, 3, new Guard(Guard.Type.IF, "flag", "then"));

        Trace t = trace(ep);

        assertThat(t.blocks()).extracting(TraceBlock::type, TraceBlock::label).containsExactly(
                org.assertj.core.groups.Tuple.tuple(TraceBlock.Type.ALT, "x > 0"),
                org.assertj.core.groups.Tuple.tuple(TraceBlock.Type.LOOP, "items"),
                org.assertj.core.groups.Tuple.tuple(TraceBlock.Type.OPT, "flag"));
        TraceBlock alt = t.blocks().getFirst();
        assertThat(alt.elseBranches()).singleElement().satisfies(e -> assertThat(e.label()).isEqualTo("ngược lại"));
        // các khối không chồng lên nhau
        assertThat(t.blocks().get(0).toStep()).isLessThan(t.blocks().get(1).fromStep());
        assertThat(t.blocks().get(1).toStep()).isLessThan(t.blocks().get(2).fromStep());
    }

    @Test
    void unresolvedCallIsShownWithoutDescendingAndWarned() {
        IrNode ep = endpoint("DELETE /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "delete", "CONTROLLER"));
        IrNode svc = method("AService", "delete", "SERVICE");
        IrNode iface = method("BQuery", "count", "SERVICE", Map.of("abstract", true));
        IrNode impl1 = method("BQueryImpl1", "count", "SERVICE");
        IrNode impl2 = method("BQueryImpl2", "count", "SERVICE");
        IrNode repo = method("BRepository", "deep", "REPOSITORY");
        calls(ctl, svc, 0);
        calls(impl1, repo, 0);
        edges.add(new IrEdge(
                svc.key(), iface.key(), EdgeKind.CALLS, Confidence.UNRESOLVED, "2 lớp hiện thực", 0, null,
                List.of(new Candidate(impl1.key(), 0.5, "a"), new Candidate(impl2.key(), 0.5, "b")), LOC));

        Trace t = trace(ep);

        assertThat(labels(t)).containsExactly("1>DELETE /a", "2>delete()", "3>count()", "4<(chưa xác định)", "5<kết quả", "6<200 OK");
        assertThat(t.steps().get(2).confidence()).isEqualTo(Confidence.UNRESOLVED);
        assertThat(t.steps().get(2).reason()).isEqualTo("2 lớp hiện thực");
        assertThat(t.warnings()).containsExactly("1 lời gọi chưa xác định được đích (xem bước 3)");
        assertThat(t.participants()).extracting(p -> p.label()).doesNotContain("Repository B");
    }

    @Test
    void pinnedOverrideReplacesTheUnresolvedTargetAndIsFollowed() {
        IrNode ep = endpoint("DELETE /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "delete", "CONTROLLER"));
        IrNode svc = method("AService", "delete", "SERVICE");
        IrNode iface = method("BQuery", "count", "SERVICE", Map.of("abstract", true));
        IrNode impl1 = method("BQueryImpl1", "count", "SERVICE");
        IrNode impl2 = method("BQueryImpl2", "count", "SERVICE");
        IrNode repo = method("BRepository", "deep", "REPOSITORY");
        calls(ctl, svc, 0);
        calls(impl1, repo, 0);
        edges.add(new IrEdge(
                svc.key(), iface.key(), EdgeKind.CALLS, Confidence.UNRESOLVED, "2 lớp hiện thực", 0, null,
                List.of(new Candidate(impl1.key(), 0.5, "a"), new Candidate(impl2.key(), 0.5, "b")), LOC));

        Trace t = trace(ep, 12, List.of(), List.of(new OverrideRule(svc.key(), impl1.key(), EdgeKind.CALLS, OverrideRule.Decision.PIN)));

        assertThat(t.steps()).filteredOn(s -> s.label().equals("count()")).singleElement().satisfies(s -> {
            assertThat(s.pinned()).isTrue();
            assertThat(s.confidence()).isEqualTo(Confidence.CERTAIN);
            assertThat(s.reason()).isEqualTo("Chốt tay");
        });
        assertThat(t.participants()).extracting(p -> p.label()).contains("Repository B");
        assertThat(t.warnings()).isEmpty();
        // chốt tay làm đổi luồng nên đổi trace_id
        assertThat(t.traceId()).isNotEqualTo(trace(ep).traceId());
    }

    @Test
    void rejectedLinkIsRemoved() {
        IrNode ep = endpoint("GET /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "get", "CONTROLLER"));
        IrNode svc = method("AService", "get", "SERVICE");
        calls(ctl, svc, 0);

        Trace t = trace(ep, 12, List.of(), List.of(new OverrideRule(ctl.key(), svc.key(), EdgeKind.CALLS, OverrideRule.Decision.REJECT)));

        assertThat(labels(t)).containsExactly("1>GET /a", "2<200 OK");
    }

    @Test
    void recursionAndDepthLimitAreCutWithAWarning() {
        IrNode ep = endpoint("GET /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "get", "CONTROLLER"));
        IrNode a = method("AService", "a", "SERVICE");
        IrNode b = method("BService", "b", "SERVICE");
        calls(ctl, a, 0);
        calls(a, b, 0);
        calls(b, a, 0); // vòng a → b → a

        Trace t = trace(ep);

        assertThat(t.warnings()).anyMatch(w -> w.contains("gọi lại chính mình (đệ quy)"));
        assertThat(t.steps().size()).isLessThan(20);

        Trace shallow = trace(ep, 2, List.of(), List.of());
        assertThat(shallow.warnings()).anyMatch(w -> w.contains("vì vượt độ sâu dò vết (tối đa 2 cấp)"));
    }

    @Test
    void sameLabelOnDifferentClassesGetsDisambiguatedAndRolesAreOrdered() {
        IrNode ep = endpoint("GET /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "get", "CONTROLLER"));
        IrNode s1 = method("AService", "get", "SERVICE");
        IrNode s2 = method("AReadService", "get", "SERVICE", Map.of("businessName", "A"));
        IrNode repo = method("ARepository", "find", "REPOSITORY");
        calls(ctl, s1, 0);
        calls(s1, s2, 0);
        calls(s2, repo, 0);

        Trace t = trace(ep);

        assertThat(t.participants()).extracting(p -> p.label()).containsExactly(
                "Client", "Controller A", "Service A (AService)", "Service A (AReadService)", "Repository A");
    }

    @Test
    void startMustBeAnExistingApiEndpoint() {
        IrNode ep = endpoint("GET /a", null);
        var table = new IrNode("t:x", NodeKind.DB_TABLE, "x", null, null, Map.of());
        nodes.add(table);

        assertThatThrownBy(() -> trace(table)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("API_ENDPOINT");
        assertThatThrownBy(() -> tracer.trace(new Subgraph(nodes, edges), new TraceRequest("ep:none", 12, "r", List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        // API chưa có hàm xử lý: vẫn trả trace rỗng kèm cảnh báo, không ném lỗi
        Trace t = trace(ep);
        assertThat(t.steps()).isEmpty();
        assertThat(t.warnings()).singleElement().asString().contains("chưa có hàm xử lý");
    }

    @Test
    void stepsAreNumberedFromOneWithoutGaps() {
        IrNode ep = endpoint("GET /a", null);
        IrNode ctl = controllerFlow(ep, method("AController", "get", "CONTROLLER"));
        IrNode svc = method("AService", "get", "SERVICE");
        calls(ctl, svc, 0);

        Trace t = trace(ep);

        assertThat(t.steps()).extracting(TraceStep::index).containsExactly(1, 2, 3, 4);
    }
}
