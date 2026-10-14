package com.ctv.flowtracer.core.linker;

import com.ctv.flowtracer.core.linker.EndpointMatcher.SegmentMatch;
import com.ctv.flowtracer.core.linker.LinkRuleEngine.Variant;
import com.ctv.flowtracer.ir.Candidate;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.LinkRule;
import com.ctv.flowtracer.ir.NodeKeys;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * CỐT LÕI: nối mỗi {@code HTTP_CALL} (FE, hoặc BE gọi service khác) với {@code API_ENDPOINT} đích, sinh edge
 * {@code HTTP_REQUEST} kèm mức tin cậy, lý do và ứng viên (Nhóm 5 của feature_list).
 *
 * <ul>
 *   <li>{@code CERTAIN}: URL là chuỗi hằng, không cần luật, khớp đúng (từng đoạn cố định) một API.
 *   <li>{@code INFERRED}: phải tính URL từ hằng số / biến, áp luật tiền tố / proxy / context-path, khớp biến đường dẫn, hoặc
 *       chỉ khớp phần cuối; ứng viên tốt nhất hơn hẳn các ứng viên còn lại.
 *   <li>{@code UNRESOLVED}: không có ứng viên, không tính được URL, hoặc nhiều ứng viên ngang điểm (lưu {@code candidates},
 *       không chọn hộ).
 *   <li>Không khớp API nào nhưng URL là địa chỉ tuyệt đối → node {@code EXTERNAL} theo host; Workspace không có Backend →
 *       mọi lời gọi tới {@code EXTERNAL} "API bên ngoài".
 * </ul>
 *
 * <p>Tất định: duyệt lời gọi và endpoint theo key, điểm chỉ từ cấu trúc đường dẫn.
 */
public final class Linker {

    /** Số ứng viên tối thiểu lưu cho một lời gọi (lấy theo điểm giảm dần). */
    static final int MAX_CANDIDATES = 5;

    /** Khi nhiều API ngang điểm thì lưu đủ các API đó (để người dùng thấy đúng số ghi trong lý do), nhưng không quá số này. */
    static final int MAX_TIED_CANDIDATES = 30;

    private static final Pattern STRING_LITERAL = Pattern.compile("^\\s*(?:'[^'$]*'|\"[^\"$]*\"|`[^`$]*`)\\s*$");

    private final LinkRuleEngine ruleEngine = new LinkRuleEngine();
    private final EndpointMatcher matcher = new EndpointMatcher();

    /**
     * Kết quả của một lần liên kết.
     *
     * @param edges edge {@code HTTP_REQUEST}, sắp theo key của lời gọi
     * @param externals node {@code EXTERNAL} được tạo, sắp theo key
     */
    public record Result(List<IrEdge> edges, List<IrNode> externals) {}

    private record Scored(IrNode endpoint, SegmentMatch match, List<LinkRule> rules, int score) {}

    /** Một biến thể đường dẫn đã phân tích sẵn (phân tích một lần, dùng cho mọi cặp lời gọi × endpoint). */
    private record Parsed(UrlPattern pattern, List<LinkRule> applied) {}

    private static List<Parsed> parse(String method, List<Variant> variants) {
        return variants.stream().map(v -> new Parsed(UrlPattern.parse(method, v.path()), v.applied())).toList();
    }

    /**
     * @param httpCalls mọi node {@code HTTP_CALL} của Workspace
     * @param endpoints mọi node {@code API_ENDPOINT} của Workspace
     * @param rules luật liên kết hiệu lực (thủ công + tự phát hiện)
     */
    public Result link(List<IrNode> httpCalls, List<IrNode> endpoints, List<LinkRule> rules) {
        List<IrNode> sortedEndpoints = endpoints.stream().sorted(Comparator.comparing(IrNode::key)).toList();
        Map<String, List<Parsed>> endpointVariants = new TreeMap<>();
        for (IrNode e : sortedEndpoints) {
            endpointVariants.put(e.key(), parse(str(e, "httpMethod"),
                    ruleEngine.backendVariants(str(e, "path"), e.attrs().get("contextPath") != null, rules)));
        }
        var edges = new ArrayList<IrEdge>();
        Map<String, IrNode> externals = new TreeMap<>();
        for (IrNode call : httpCalls.stream().sorted(Comparator.comparing(IrNode::key)).toList()) {
            edges.add(linkOne(call, sortedEndpoints, endpointVariants, rules, externals));
        }
        return new Result(edges, List.copyOf(externals.values()));
    }

    private IrEdge linkOne(
            IrNode call,
            List<IrNode> endpoints,
            Map<String, List<Parsed>> endpointVariants,
            List<LinkRule> rules,
            Map<String, IrNode> externals) {
        String method = str(call, "httpMethod");
        String pattern = str(call, "urlPattern");
        String host = str(call, "urlHost");
        String label = method + " " + (pattern.isEmpty() ? str(call, "urlExpr") : pattern);
        if (endpoints.isEmpty()) {
            String key = external(externals, NodeKeys.EXTERNAL_API, "API bên ngoài", host);
            return edge(call, key, Confidence.INFERRED,
                    "Workspace không có mã nguồn Backend nên lời gọi " + label + " đi tới API bên ngoài", List.of());
        }
        if (pattern.isEmpty()) {
            String issue = str(call, "urlIssue");
            if (!host.isEmpty()) {
                return edge(call, external(externals, host, host, host), Confidence.INFERRED,
                        "Lời gọi tới địa chỉ tuyệt đối " + host + " (không tính được đường dẫn)", List.of());
            }
            return edge(call, null, Confidence.UNRESOLVED,
                    "Không tính được URL của lời gọi" + (issue.isEmpty() ? "" : ": " + issue), List.of());
        }
        boolean resolved = !Boolean.FALSE.equals(call.attrs().get("urlResolved"));
        List<Parsed> callVariants = parse(method, ruleEngine.frontendVariants(pattern, rules));
        List<Scored> matches = new ArrayList<>();
        for (IrNode endpoint : endpoints) {
            Scored best = null;
            for (Parsed cv : callVariants) {
                for (Parsed ev : endpointVariants.get(endpoint.key())) {
                    var found = matcher.match(cv.pattern(), ev.pattern(), !resolved);
                    if (found.isEmpty()) {
                        continue;
                    }
                    List<LinkRule> used = new ArrayList<>(cv.applied());
                    used.addAll(ev.applied());
                    int score = found.get().score() - used.size() * CandidateScorer.RULE_PENALTY;
                    if (best == null || score > best.score()) {
                        best = new Scored(endpoint, found.get(), used, score);
                    }
                }
            }
            if (best != null) {
                matches.add(best);
            }
        }
        matches.sort(Comparator.comparingInt(Scored::score).reversed().thenComparing(s -> s.endpoint().key()));

        if (matches.isEmpty()) {
            if (!host.isEmpty()) {
                return edge(call, external(externals, host, host, host), Confidence.INFERRED,
                        "Lời gọi tới địa chỉ tuyệt đối " + host + ", không khớp API nào trong Workspace", List.of());
            }
            return edge(call, null, Confidence.UNRESOLVED, "Không có API nào trong Workspace khớp " + label, List.of());
        }
        Scored best = matches.get(0);
        long tied = matches.stream().filter(m -> best.score() - m.score() < CandidateScorer.TIE_MARGIN).count();
        int limit = (int) Math.min(MAX_TIED_CANDIDATES, Math.max(MAX_CANDIDATES, tied));
        List<Candidate> candidates = matches.stream().limit(limit).map(Linker::candidate).toList();
        if (tied > 1) {
            return edge(call, null, Confidence.UNRESOLVED,
                    tied + " API khớp " + label + " với mức gần như nhau nên chưa kết luận; hãy chọn một ứng viên"
                            + (tied > limit ? " (hiện " + limit + " ứng viên đầu)" : ""),
                    candidates);
        }
        boolean literalUrl = STRING_LITERAL.matcher(str(call, "urlExpr")).matches();
        if (best.match().exact() && best.rules().isEmpty() && resolved && literalUrl && !"false".equals(str(best.endpoint(), "pathResolved"))) {
            return edge(call, best.endpoint().key(), Confidence.CERTAIN,
                    "URL là chuỗi hằng khớp đúng API " + best.endpoint().name(), List.of());
        }
        return edge(call, best.endpoint().key(), Confidence.INFERRED, inferredReason(call, best, matches), matches.size() > 1 ? candidates : List.of());
    }

    /** Lý do của liên kết suy luận: vì sao chưa phải chắc chắn và đã dùng những gì để nối. */
    private static String inferredReason(IrNode call, Scored best, List<Scored> matches) {
        Set<String> parts = new LinkedHashSet<>();
        String expr = str(call, "urlExpr");
        if (!STRING_LITERAL.matcher(expr).matches() && !expr.isEmpty()) {
            parts.add("URL ghép từ " + shorten(expr));
        }
        for (LinkRule rule : best.rules()) {
            parts.add("áp luật " + describe(rule));
        }
        if (best.match().suffix()) {
            String issue = str(call, "urlIssue");
            parts.add("phần đầu URL không tính được" + (issue.isEmpty() ? "" : " (" + issue + ")") + " nên chỉ so khớp phần cuối");
        } else if (best.match().variables()) {
            parts.add("khớp biến đường dẫn");
        }
        if ("ANY".equals(str(best.endpoint(), "httpMethod"))) {
            parts.add("API nhận mọi phương thức HTTP");
        }
        if (matches.size() > 1) {
            parts.add("chọn " + best.endpoint().name() + " vì khớp rõ hơn " + matches.get(1).endpoint().name());
        }
        String text = String.join("; ", parts);
        return text.isEmpty() ? "Khớp " + best.endpoint().name() : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static Candidate candidate(Scored s) {
        double normalized = CandidateScorer.normalize(s.score(), s.match().segmentCount());
        String detail = s.match().exact()
                ? "khớp đúng từng đoạn đường dẫn"
                : s.match().suffix() ? "chỉ khớp phần cuối đường dẫn" : "khớp đường dẫn có biến";
        if (!s.rules().isEmpty()) {
            detail += ", áp " + s.rules().size() + " luật";
        }
        return new Candidate(s.endpoint().key(), normalized, "Mức khớp " + CandidateScorer.level(normalized).label() + ": " + detail);
    }

    static String describe(LinkRule rule) {
        if (rule.description() != null && !rule.description().isBlank()) {
            return "'" + rule.pattern() + "' → '" + (rule.replacement() == null ? "" : rule.replacement()) + "' (" + rule.description() + ")";
        }
        return "'" + rule.pattern() + "' → '" + (rule.replacement() == null ? "" : rule.replacement()) + "'";
    }

    private static String external(Map<String, IrNode> externals, String name, String displayName, String url) {
        String key = NodeKeys.external(name);
        externals.computeIfAbsent(key, k -> new IrNode(
                k, NodeKind.EXTERNAL, displayName, displayName, null,
                url.isEmpty() ? Map.of("reason", "Đích ngoài Workspace") : Map.of("url", url, "reason", "Đích ngoài Workspace")));
        return key;
    }

    private static IrEdge edge(IrNode call, String toKey, Confidence confidence, String reason, List<Candidate> candidates) {
        return new IrEdge(call.key(), toKey, EdgeKind.HTTP_REQUEST, confidence, reason, null, null, candidates, call.location());
    }

    private static String str(IrNode node, String attr) {
        Object value = node.attrs().get(attr);
        return value == null ? "" : value.toString();
    }

    private static String shorten(String expr) {
        String oneLine = expr.replaceAll("\\s+", " ").strip();
        return oneLine.length() > 80 ? oneLine.substring(0, 79) + "…" : oneLine;
    }
}
