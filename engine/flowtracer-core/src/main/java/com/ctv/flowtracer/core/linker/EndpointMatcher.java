package com.ctv.flowtracer.core.linker;

import com.ctv.flowtracer.core.linker.UrlPattern.Segment;
import com.ctv.flowtracer.core.linker.UrlPattern.SegmentType;
import java.util.List;
import java.util.Optional;

/**
 * So khớp đường dẫn của một lời gọi HTTP với đường dẫn của một endpoint: phương thức phải trùng (endpoint {@code ANY} nhận
 * mọi phương thức nhưng bị trừ điểm), số đoạn phải bằng nhau, từng cặp đoạn phải khớp. Điểm theo {@link CandidateScorer}.
 *
 * <p>Khi phần đầu URL của lời gọi không tính được (vd {@code {API_BASE}/items}) và được phép so khớp phần cuối, đoạn biến đầu
 * tiên được "nuốt" một hoặc nhiều đoạn đầu của endpoint.
 */
public final class EndpointMatcher {

    /**
     * Kết quả so khớp.
     *
     * @param score điểm thô
     * @param exact mọi đoạn là literal bằng nhau và phương thức trùng
     * @param variables có cặp đoạn khớp nhờ biến
     * @param suffix chỉ khớp phần cuối (phần đầu URL không tính được)
     * @param segmentCount số đoạn dài hơn của hai bên (để chuẩn hóa điểm)
     */
    public record SegmentMatch(int score, boolean exact, boolean variables, boolean suffix, int segmentCount) {}

    /** @param allowSuffix cho phép đoạn biến đầu tiên của lời gọi nuốt nhiều đoạn (phần đầu URL không tính được) */
    public Optional<SegmentMatch> match(UrlPattern call, UrlPattern endpoint, boolean allowSuffix) {
        boolean anyMethod = UrlPattern.ANY.equals(endpoint.httpMethod());
        if (!anyMethod && !endpoint.httpMethod().equals(call.httpMethod())) {
            return Optional.empty();
        }
        int penalty = anyMethod ? CandidateScorer.ANY_METHOD_PENALTY : 0;
        List<Segment> c = call.segments();
        List<Segment> e = endpoint.segments();
        int count = Math.max(c.size(), e.size());
        if (c.size() == e.size()) {
            Optional<Integer> score = pairs(c, 0, e, 0);
            if (score.isPresent()) {
                boolean exact = !anyMethod && c.stream().allMatch(Segment::literal) && e.stream().allMatch(Segment::literal);
                boolean variables = !(c.stream().allMatch(Segment::literal) && e.stream().allMatch(Segment::literal));
                return Optional.of(new SegmentMatch(score.get() - penalty, exact, variables, false, count));
            }
        }
        // phần cuối của lời gọi (sau đoạn biến đầu) phải còn ít nhất một đoạn để so
        if (allowSuffix && call.startsWithVariable() && c.size() >= 2 && e.size() >= c.size()) {
            int absorbed = e.size() - c.size() + 1;
            Optional<Integer> score = pairs(c, 1, e, absorbed);
            if (score.isPresent()) {
                int total = score.get() + absorbed * CandidateScorer.ABSORBED - CandidateScorer.SUFFIX_PENALTY - penalty;
                return Optional.of(new SegmentMatch(total, false, true, true, e.size()));
            }
        }
        return Optional.empty();
    }

    /** Điểm khi so từng cặp đoạn từ vị trí cho trước; rỗng nếu một cặp không khớp. */
    private static Optional<Integer> pairs(List<Segment> call, int ci, List<Segment> endpoint, int ei) {
        int score = 0;
        for (; ci < call.size() && ei < endpoint.size(); ci++, ei++) {
            int s = pair(call.get(ci), endpoint.get(ei));
            if (s < 0) {
                return Optional.empty();
            }
            score += s;
        }
        return Optional.of(score);
    }

    private static int pair(Segment call, Segment endpoint) {
        SegmentType a = call.type();
        SegmentType b = endpoint.type();
        if (a == SegmentType.LITERAL && b == SegmentType.LITERAL) {
            return call.value().equals(endpoint.value()) ? CandidateScorer.LITERAL_LITERAL : -1;
        }
        if (a == SegmentType.VARIABLE && b == SegmentType.VARIABLE) {
            return CandidateScorer.VARIABLE_VARIABLE;
        }
        if (a == SegmentType.LITERAL && b == SegmentType.VARIABLE) {
            return CandidateScorer.LITERAL_VARIABLE;
        }
        if (a == SegmentType.VARIABLE && b == SegmentType.LITERAL) {
            return CandidateScorer.VARIABLE_LITERAL;
        }
        if (a == SegmentType.LITERAL && b == SegmentType.MIXED) {
            return endpoint.regex().matcher(call.value()).matches() ? CandidateScorer.MIXED : -1;
        }
        if (a == SegmentType.MIXED && b == SegmentType.LITERAL) {
            return call.regex().matcher(endpoint.value()).matches() ? CandidateScorer.MIXED : -1;
        }
        // biến gặp đoạn trộn, hoặc hai đoạn trộn: không kiểm được chắc chắn, coi như biến gặp literal
        return CandidateScorer.VARIABLE_LITERAL;
    }
}
