package com.ctv.flowtracer.core.linker;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.ir.Candidate;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.LinkRule;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Nối lời gọi HTTP với API theo Nhóm 5: mức tin cậy, luật, đoạn cố định thắng đoạn biến, ứng viên ngang điểm. */
class LinkerTest {

    private static final LinkRule PROXY = new LinkRule(LinkRule.Type.REWRITE, "^/gateway", "", LinkRule.Origin.AUTO, "proxy.conf.json");

    private final Linker linker = new Linker();
    private final List<IrNode> endpoints = new ArrayList<>();

    private void endpoint(String method, String path) {
        endpoints.add(new IrNode("java:endpoint:" + method + " " + path, NodeKind.API_ENDPOINT, method + " " + path, null,
                SourceLocation.of("Api.java", 1), Map.of("httpMethod", method, "path", path)));
    }

    private static IrNode call(String key, String method, String pattern, String expr, Map<String, Object> extra) {
        Map<String, Object> attrs = new HashMap<>(Map.of("httpMethod", method, "urlExpr", expr, "urlResolved", true, "origin", "FE"));
        if (pattern != null) {
            attrs.put("urlPattern", pattern);
        }
        attrs.putAll(extra);
        return new IrNode(key, NodeKind.HTTP_CALL, method + " " + pattern, null, SourceLocation.of("repo.ts", 3), attrs);
    }

    private IrEdge linkOne(IrNode call, LinkRule... rules) {
        return linker.link(List.of(call), endpoints, List.of(rules)).edges().getFirst();
    }

    @Test
    void literalUrlMatchingExactlyOneApiIsCertain() {
        endpoint("GET", "/api/v1/items");
        IrEdge edge = linkOne(call("c", "GET", "/api/v1/items", "'/api/v1/items'", Map.of()));

        assertThat(edge.kind()).isEqualTo(EdgeKind.HTTP_REQUEST);
        assertThat(edge.toKey()).isEqualTo("java:endpoint:GET /api/v1/items");
        assertThat(edge.confidence()).isEqualTo(Confidence.CERTAIN);
        assertThat(edge.candidates()).isEmpty();
        assertThat(edge.location()).isNotNull();
    }

    @Test
    void urlFromConstantAndProxyRuleIsInferredWithReason() {
        endpoint("POST", "/api/v1/items");
        IrEdge edge = linkOne(call("c", "POST", "/gateway/api/v1/items", "ITEM_BASE", Map.of()), PROXY);

        assertThat(edge.toKey()).isEqualTo("java:endpoint:POST /api/v1/items");
        assertThat(edge.confidence()).isEqualTo(Confidence.INFERRED);
        assertThat(edge.reason()).contains("URL ghép từ ITEM_BASE").contains("'^/gateway' → ''").contains("proxy.conf.json");
    }

    @Test
    void methodMustMatch() {
        endpoint("GET", "/api/v1/items");
        IrEdge edge = linkOne(call("c", "DELETE", "/api/v1/items", "'/api/v1/items'", Map.of()));

        assertThat(edge.confidence()).isEqualTo(Confidence.UNRESOLVED);
        assertThat(edge.toKey()).isNull();
        assertThat(edge.reason()).contains("Không có API nào");
    }

    @Test
    void fixedSegmentBeatsVariableSegment() {
        endpoint("GET", "/items/{id}");
        endpoint("GET", "/items/export-excel");
        IrEdge edge = linkOne(call("c", "GET", "/items/export-excel", "BASE + '/export-excel'", Map.of()));

        assertThat(edge.toKey()).isEqualTo("java:endpoint:GET /items/export-excel");
        assertThat(edge.confidence()).isEqualTo(Confidence.INFERRED);
        // Ứng viên vẫn được giữ để người dùng xem, mức khớp cao trước
        assertThat(edge.candidates()).extracting(Candidate::nodeKey)
                .containsExactly("java:endpoint:GET /items/export-excel", "java:endpoint:GET /items/{id}");
        assertThat(CandidateScorer.level(edge.candidates().get(0).score())).isEqualTo(CandidateScorer.Level.HIGH);
    }

    @Test
    void pathVariableMatchesVariableSegment() {
        endpoint("DELETE", "/api/v1/items/{id}");
        IrEdge edge = linkOne(call("c", "DELETE", "/api/v1/items/{id}", "`${BASE}/${id}`", Map.of()));

        assertThat(edge.toKey()).isEqualTo("java:endpoint:DELETE /api/v1/items/{id}");
        assertThat(edge.confidence()).isEqualTo(Confidence.INFERRED);
        assertThat(edge.reason()).contains("khớp biến đường dẫn");
    }

    @Test
    void nearlyEqualCandidatesStayUnresolvedWithCandidates() {
        endpoint("GET", "/a/{id}/items");
        endpoint("GET", "/a/{code}/items");
        IrEdge edge = linkOne(call("c", "GET", "/a/{x}/items", "url", Map.of()));

        assertThat(edge.confidence()).isEqualTo(Confidence.UNRESOLVED);
        assertThat(edge.toKey()).isNull();
        assertThat(edge.candidates()).hasSize(2);
        assertThat(edge.reason()).contains("2 API khớp");
    }

    @Test
    void unknownBaseUrlMatchesTheSuffix() {
        endpoint("GET", "/api/v1/items");
        endpoint("GET", "/api/v1/orders");
        IrNode call = call("c", "GET", "{apiBase}/items", "this.apiBase + '/items'",
                Map.of("urlResolved", false, "urlIssue", "Không tìm thấy giá trị của apiBase"));

        IrEdge edge = linkOne(call);

        assertThat(edge.toKey()).isEqualTo("java:endpoint:GET /api/v1/items");
        assertThat(edge.confidence()).isEqualTo(Confidence.INFERRED);
        assertThat(edge.reason()).contains("phần đầu URL không tính được").contains("apiBase");
    }

    @Test
    void manualPrefixRuleReplacesTheUnknownBase() {
        endpoint("GET", "/api/v1/items");
        endpoint("GET", "/internal/items");
        LinkRule prefix = new LinkRule(LinkRule.Type.PREFIX, "{apiBase}", "/api/v1", LinkRule.Origin.MANUAL, null);
        IrNode call = call("c", "GET", "{apiBase}/items", "this.apiBase + '/items'", Map.of("urlResolved", false));

        IrEdge edge = linkOne(call, prefix);

        assertThat(edge.toKey()).isEqualTo("java:endpoint:GET /api/v1/items");
        assertThat(edge.reason()).contains("'{apiBase}' → '/api/v1'");
    }

    @Test
    void manualContextPathRuleIsAppliedToEndpointsWithoutOne() {
        endpoint("GET", "/items");
        LinkRule contextPath = new LinkRule(LinkRule.Type.CONTEXT_PATH, "/shop", null, LinkRule.Origin.MANUAL, null);

        IrEdge edge = linkOne(call("c", "GET", "/shop/items", "'/shop/items'", Map.of()), contextPath);

        assertThat(edge.toKey()).isEqualTo("java:endpoint:GET /items");
        assertThat(edge.confidence()).isEqualTo(Confidence.INFERRED);
    }

    @Test
    void absoluteUrlToUnknownHostBecomesExternal() {
        endpoint("GET", "/api/v1/items");
        IrNode call = call("c", "POST", "/v1/charges", "'https://pay.example.com/v1/charges'", Map.of("urlHost", "pay.example.com"));

        Linker.Result result = linker.link(List.of(call), endpoints, List.of());

        assertThat(result.edges().getFirst().toKey()).isEqualTo("ext:system:pay.example.com");
        assertThat(result.externals()).singleElement().satisfies(n -> {
            assertThat(n.kind()).isEqualTo(NodeKind.EXTERNAL);
            assertThat(n.name()).isEqualTo("pay.example.com");
        });
    }

    @Test
    void frontendOnlyWorkspaceCallsTheExternalApi() {
        Linker.Result result = linker.link(List.of(call("c", "GET", "/api/x", "'/api/x'", Map.of())), List.of(), List.of());

        assertThat(result.edges().getFirst().toKey()).isEqualTo("ext:system:api");
        assertThat(result.externals()).singleElement().satisfies(n -> assertThat(n.name()).isEqualTo("API bên ngoài"));
    }

    @Test
    void urlThatCannotBeComputedIsUnresolvedWithTheIssue() {
        endpoint("GET", "/api/v1/items");
        IrNode call = call("c", "GET", null, "buildUrl()", Map.of("urlResolved", false, "urlIssue", "Biểu thức không tính được: buildUrl()"));

        IrEdge edge = linkOne(call);

        assertThat(edge.confidence()).isEqualTo(Confidence.UNRESOLVED);
        assertThat(edge.reason()).contains("buildUrl()");
    }

    @Test
    void anyMethodEndpointMatchesWithPenalty() {
        endpoint("ANY", "/legacy/items");
        IrEdge edge = linkOne(call("c", "PUT", "/legacy/items", "'/legacy/items'", Map.of()));

        assertThat(edge.toKey()).isEqualTo("java:endpoint:ANY /legacy/items");
        assertThat(edge.confidence()).isEqualTo(Confidence.INFERRED);
        assertThat(edge.reason()).contains("mọi phương thức");
    }

    @Test
    void resultIsDeterministic() {
        endpoint("GET", "/a/{id}");
        endpoint("GET", "/a/b");
        List<IrNode> calls = List.of(
                call("z", "GET", "/a/{x}", "u", Map.of()), call("a", "GET", "/a/b", "'/a/b'", Map.of()));

        assertThat(linker.link(calls, endpoints, List.of(PROXY))).isEqualTo(linker.link(calls.reversed(), endpoints.reversed(), List.of(PROXY)));
    }

    @Test
    void urlPatternNormalizesVariablesAndQuery() {
        UrlPattern p = UrlPattern.parse("get", "https://h.example/api//items/:id/{code:[a-z]+}/x-{n}?q=1");

        assertThat(p.httpMethod()).isEqualTo("GET");
        assertThat(p.path()).isEqualTo("/api/items/{id}/{code}/x-{n}");
        assertThat(p.segments()).extracting(UrlPattern.Segment::type).containsExactly(
                UrlPattern.SegmentType.LITERAL, UrlPattern.SegmentType.LITERAL, UrlPattern.SegmentType.VARIABLE,
                UrlPattern.SegmentType.VARIABLE, UrlPattern.SegmentType.MIXED);
    }

    @Test
    void invalidRewriteRuleIsReported() {
        assertThat(LinkRuleEngine.validate(new LinkRule(LinkRule.Type.REWRITE, "^/(api", "", LinkRule.Origin.MANUAL, null))).isPresent();
        assertThat(LinkRuleEngine.validate(PROXY)).isEmpty();
    }
}
