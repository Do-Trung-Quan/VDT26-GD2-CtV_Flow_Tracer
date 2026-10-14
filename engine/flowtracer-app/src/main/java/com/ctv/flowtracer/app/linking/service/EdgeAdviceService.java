package com.ctv.flowtracer.app.linking.service;

import com.ctv.flowtracer.app.ai.model.AiCall;
import com.ctv.flowtracer.app.ai.model.AiOutcome;
import com.ctv.flowtracer.app.ai.model.AiPurpose;
import com.ctv.flowtracer.app.ai.model.AiRejectedException;
import com.ctv.flowtracer.app.ai.service.AiGateway;
import com.ctv.flowtracer.app.ai.service.PromptTemplates;
import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.explanation.dto.response.CodeTabResponse;
import com.ctv.flowtracer.app.explanation.service.CodeSnippetService;
import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.service.GraphQueryService;
import com.ctv.flowtracer.app.linking.dto.response.EdgeAdviceResponse;
import com.ctv.flowtracer.app.linking.dto.response.EdgeCandidateResponse;
import com.ctv.flowtracer.app.linking.dto.response.EdgeDetailResponse;
import com.ctv.flowtracer.app.linking.dto.response.EdgeNodeResponse;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * API E2 "Nhờ AI phân tích": AI giải thích từng ứng viên của một liên kết chưa chắc chắn (nên / không nên chọn, kèm dẫn chứng từ
 * đoạn mã) và được đề xuất thêm TỐI ĐA MỘT ứng viên. Không bao giờ đổi liên kết (CLAUDE.md §3.7): đề xuất thêm chỉ được hiện khi qua
 * {@link AiAdviceRule}. Chỉ chạy khi người dùng bấm nút.
 */
@Service
public class EdgeAdviceService {

    private static final int MAX_EXTRAS = 15;
    private static final int MAX_CODE_CHARS = 5000;
    private static final Set<String> ADVICE = Set.of("SHOULD", "SHOULD_NOT", "UNSURE");

    private final LinkingService linkingService;
    private final GraphQueryService graphQueries;
    private final CodeSnippetService codeSnippets;
    private final AiGateway gateway;
    private final PromptTemplates prompts;
    private final CallChainService callChains;
    private final AiAdviceRule rule = new AiAdviceRule();

    public EdgeAdviceService(
            LinkingService linkingService,
            GraphQueryService graphQueries,
            CodeSnippetService codeSnippets,
            AiGateway gateway,
            PromptTemplates prompts,
            CallChainService callChains) {
        this.callChains = callChains;
        this.linkingService = linkingService;
        this.graphQueries = graphQueries;
        this.codeSnippets = codeSnippets;
        this.gateway = gateway;
        this.prompts = prompts;
    }

    /**
     * @throws AppException {@code EDGE_NOT_FOUND}; {@code EDGE_HAS_NO_CANDIDATES} nếu không có gì để phân tích; {@code 503}
     *     {@code AI_*} khi AI tắt, hết hạn mức hoặc lỗi
     */
    public EdgeAdviceResponse advise(UUID workspaceId, UUID edgeId, boolean force) {
        return advise(workspaceId, edgeId, force, null);
    }

    /**
     * @param startKey thành phần xuất phát của luồng đang xem: có thì AI nhận thêm chuỗi gọi từ đó tới điểm gọi (tên hàm, chuỗi hằng
     *     truyền vào) và các từ khớp tên của từng ứng viên — bằng chứng nằm phía TRÊN điểm gọi chứ không ở điểm gọi
     */
    public EdgeAdviceResponse advise(UUID workspaceId, UUID edgeId, boolean force, String startKey) {
        EdgeDetailResponse detail = linkingService.edgeDetail(workspaceId, edgeId, startKey);
        if (!detail.overridable()) {
            throw new AppException(ErrorCode.EDGE_HAS_NO_CANDIDATES, "Liên kết này không có ứng viên để phân tích");
        }
        Map<String, EdgeCandidateResponse> candidates = new LinkedHashMap<>();
        for (EdgeCandidateResponse c : candidates(detail)) {
            candidates.put("c" + (candidates.size() + 1), c);
        }
        Map<String, IrNode> extras = extras(workspaceId, detail);
        if (candidates.isEmpty() && extras.isEmpty()) {
            throw new AppException(ErrorCode.EDGE_HAS_NO_CANDIDATES, "Chưa có ứng viên nào để AI phân tích");
        }
        String candidateText = candidates.entrySet().stream()
                .map(e -> e.getKey() + " | " + name(e.getValue()) + " | mức khớp: " + e.getValue().levelLabel() + " | lý do của tool: " + e.getValue().reason())
                .collect(Collectors.joining("\n"));
        String extraText = extras.isEmpty()
                ? "(không có)"
                : extras.entrySet().stream().map(e -> e.getKey() + " | " + e.getValue().name()
                                + (e.getValue().displayName() == null ? "" : " (" + e.getValue().displayName() + ")")).collect(Collectors.joining("\n"));
        CallChainService.CallChain chain =
                detail.from() == null ? CallChainService.CallChain.EMPTY : callChains.chain(workspaceId, startKey, detail.from().nodeKey());
        String chainText = chain.isEmpty() ? "(không xác định được chuỗi gọi)" : chain.render();
        String code = code(workspaceId, detail);
        String hintText = hintText(candidates);
        var rendered = prompts.render("edge-resolution.v2", Map.of(
                "kind", detail.kind() == EdgeKind.HTTP_REQUEST ? "lời gọi HTTP tới một API" : "lời gọi hàm",
                "confidence", String.valueOf(detail.confidence()),
                "reason", detail.reason() == null ? "(không có)" : detail.reason(),
                "from", detail.from() == null ? "(không rõ)" : detail.from().name(),
                "chain", chainText,
                "code", code,
                "candidates", candidates.isEmpty() ? "(không có)" : candidateText,
                "hints", hintText,
                "extras", extraText));
        String context = chainText + "\n" + code + "\n" + candidateText + "\n" + extraText;
        Map<String, Integer> hintCounts = new LinkedHashMap<>();
        candidates.values().forEach(c -> hintCounts.put(c.nodeKey(), c.hints() == null ? 0 : c.hints().size()));
        String httpMethod = httpMethod(detail);
        // khóa theo cả chuỗi gọi: cùng một liên kết (vd hàm get(resource) dùng chung) có lời giải thích khác nhau tùy luồng đang xem
        String target = chain.isEmpty() ? edgeId.toString() : edgeId + "#" + Integer.toHexString(chain.render().hashCode());
        AiCall call = new AiCall(workspaceId, AiPurpose.EDGE_RESOLUTION, target, rendered.version(), rendered.system(),
                rendered.user(), rendered.schema(), 2000, raw -> validate(raw, candidates, extras, detail.kind(), httpMethod, hintCounts, context));
        AiOutcome outcome = gateway.execute(call, force);
        if (!outcome.hasContent()) {
            throw gateway.failure(outcome);
        }
        return toResponse(edgeId, outcome, workspaceId);
    }

    // ---------------- đầu vào ----------------

    /**
     * Các ứng viên để AI giải thích. Liên kết chỉ khớp MỘT API (suy luận) không lưu danh sách ứng viên: đích tự động hiện tại chính là
     * ứng viên duy nhất — đưa nó vào để AI cho biết có nên tin không (trước đây nó lọt vào "có thể chọn thêm" nên lời giải thích không
     * gắn được vào dòng nào của popover).
     */
    static List<EdgeCandidateResponse> candidates(EdgeDetailResponse detail) {
        if (!detail.candidates().isEmpty() || detail.to() == null || detail.to().nodeKey() == null) {
            return detail.candidates();
        }
        return List.of(new EdgeCandidateResponse(
                detail.to(), detail.to().nodeKey(), null, "đích tự động (ứng viên duy nhất)", "phân tích tĩnh chỉ tìm được đích này", true, List.of()));
    }

    /** Thành phần có thể chọn thêm: API cùng phương thức HTTP chưa là ứng viên, xếp theo số từ chung với tên lời gọi. */
    private Map<String, IrNode> extras(UUID workspaceId, EdgeDetailResponse detail) {
        Map<String, IrNode> result = new LinkedHashMap<>();
        if (detail.kind() != EdgeKind.HTTP_REQUEST) {
            return result;
        }
        String method = httpMethod(detail);
        Set<String> taken = candidates(detail).stream().map(EdgeCandidateResponse::nodeKey).collect(Collectors.toSet());
        Set<String> words = words(detail.from() == null ? "" : detail.from().name());
        List<IrNode> ranked = graphQueries.nodesOfKind(workspaceId, NodeKind.API_ENDPOINT).stream()
                .map(IrNodeRow::node)
                .filter(n -> !taken.contains(n.key()))
                .filter(n -> rule.acceptExtra(EdgeKind.HTTP_REQUEST, method, n).isPresent())
                .sorted(Comparator.comparingLong((IrNode n) -> -words(n.name()).stream().filter(words::contains).count()).thenComparing(IrNode::key))
                .limit(MAX_EXTRAS)
                .toList();
        for (IrNode n : ranked) {
            result.put("x" + (result.size() + 1), n);
        }
        return result;
    }

    private static String hintText(Map<String, EdgeCandidateResponse> candidates) {
        String text = candidates.entrySet().stream()
                .filter(e -> e.getValue().hints() != null && !e.getValue().hints().isEmpty())
                .map(e -> e.getKey() + ": " + String.join(", ", e.getValue().hints()))
                .collect(Collectors.joining("\n"));
        return text.isEmpty() ? "(không có ứng viên nào khớp tên với chuỗi gọi)" : text;
    }

    private String code(UUID workspaceId, EdgeDetailResponse detail) {
        if (detail.from() == null) {
            return "(không đọc được mã nguồn)";
        }
        try {
            List<CodeTabResponse> tabs = codeSnippets.snippets(workspaceId, detail.from().nodeKey(), 3);
            String text = tabs.stream().findFirst().map(CodeTabResponse::code).orElse("(không có)");
            return text.length() > MAX_CODE_CHARS ? text.substring(0, MAX_CODE_CHARS) : text;
        } catch (RuntimeException e) {
            return "(không đọc được mã nguồn)";
        }
    }

    private static String httpMethod(EdgeDetailResponse detail) {
        if (detail.from() == null) {
            return null;
        }
        String name = detail.from().name();
        int space = name.indexOf(' ');
        String first = space > 0 ? name.substring(0, space).toUpperCase(Locale.ROOT) : "";
        return Set.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(first) ? first : null;
    }

    private static Set<String> words(String text) {
        Set<String> set = new HashSet<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() > 2) {
                set.add(w);
            }
        }
        return set;
    }

    private static String name(EdgeCandidateResponse c) {
        return c.node() == null ? c.nodeKey() : c.node().name() + (c.node().displayName() == null ? "" : " (" + c.node().displayName() + ")");
    }

    // ---------------- đầu ra ----------------

    private Map<String, Object> validate(
            Map<String, Object> raw,
            Map<String, EdgeCandidateResponse> candidates,
            Map<String, IrNode> extras,
            EdgeKind kind,
            String httpMethod,
            Map<String, Integer> hintCounts,
            String context) {
        var parsed = new ArrayList<AdviceConsistencyRule.Item>();
        if (raw.get("analyses") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("id") instanceof String id && candidates.containsKey(id)
                        && m.get("advice") instanceof String advice && ADVICE.contains(advice)
                        && m.get("reason") instanceof String reason && !reason.isBlank()) {
                    String evidence = m.get("evidence") instanceof String e ? (e.length() > 80 ? e.substring(0, 80) : e) : null;
                    parsed.add(new AdviceConsistencyRule.Item(
                            candidates.get(id).nodeKey(), advice, reason.length() > 400 ? reason.substring(0, 400) : reason.strip(), evidence));
                }
            }
        }
        var analyses = new ArrayList<Map<String, Object>>();
        for (AdviceConsistencyRule.Item i : AdviceConsistencyRule.apply(parsed, hintCounts, context)) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("nodeKey", i.nodeKey());
            entry.put("advice", i.advice());
            entry.put("reason", i.reason());
            if (i.evidence() != null) {
                entry.put("evidence", i.evidence());
            }
            analyses.add(entry);
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("analyses", analyses);
        Map<String, Object> extra = null;
        if (raw.get("extra") instanceof Map<?, ?> m && m.get("id") instanceof String id && extras.containsKey(id)
                && m.get("reason") instanceof String reason && !reason.isBlank()
                && rule.acceptExtra(kind, httpMethod, extras.get(id)).isPresent()) {
            extra = Map.of("nodeKey", extras.get(id).key(), "reason", reason.length() > 400 ? reason.substring(0, 400) : reason.strip());
        }
        content.put("extra", extra);
        if (analyses.isEmpty() && extra == null) {
            throw new AiRejectedException("AI không giải thích được ứng viên nào hợp lệ");
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    private EdgeAdviceResponse toResponse(UUID edgeId, AiOutcome outcome, UUID workspaceId) {
        var analyses = new ArrayList<EdgeAdviceResponse.CandidateAdvice>();
        for (Map<String, Object> a : (List<Map<String, Object>>) outcome.content().get("analyses")) {
            analyses.add(new EdgeAdviceResponse.CandidateAdvice(
                    String.valueOf(a.get("nodeKey")), String.valueOf(a.get("advice")), String.valueOf(a.get("reason")),
                    a.get("evidence") instanceof String ev ? ev : null));
        }
        EdgeAdviceResponse.ExtraCandidate extra = null;
        if (outcome.content().get("extra") instanceof Map<?, ?> e) {
            String key = String.valueOf(e.get("nodeKey"));
            extra = graphQueries.nodesByKeys(workspaceId, List.of(key)).stream().findFirst()
                    .map(r -> new EdgeAdviceResponse.ExtraCandidate(EdgeNodeResponse.from(r.node()), String.valueOf(e.get("reason"))))
                    .orElse(null);
        }
        return new EdgeAdviceResponse(edgeId.toString(), outcome.status().name(), outcome.provider(), outcome.model(), analyses, extra);
    }
}
