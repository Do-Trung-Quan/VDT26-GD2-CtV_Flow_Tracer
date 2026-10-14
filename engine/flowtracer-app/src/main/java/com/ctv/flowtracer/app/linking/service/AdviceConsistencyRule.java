package com.ctv.flowtracer.app.linking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kiểm tra nhất quán lời giải thích của AI cho các ứng viên của một liên kết (thuần, tất định):
 * <ul>
 *   <li>"Nên chọn" phải kèm BẰNG CHỨNG là từ / chuỗi có thật trong chuỗi gọi và mã đã đưa cho AI; không thì hạ xuống "Chưa chắc";
 *   <li>một lời gọi chỉ có một đích: nếu AI "Nên chọn" nhiều ứng viên thì chỉ giữ ứng viên có nhiều tín hiệu khớp tên nhất (khi duy
 *       nhất), còn lại hạ xuống "Chưa chắc".
 * </ul>
 */
final class AdviceConsistencyRule {

    static final String SHOULD = "SHOULD";
    static final String UNSURE = "UNSURE";

    /** Lời giải thích của AI cho một ứng viên; {@code evidence} có thể null. */
    record Item(String nodeKey, String advice, String reason, String evidence) {}

    private AdviceConsistencyRule() {}

    /**
     * @param hintCounts khóa ứng viên → số từ khớp tên (từ {@link CandidateHintRule})
     * @param context toàn bộ chữ đã đưa cho AI (chuỗi gọi, mã, ứng viên) để kiểm tra bằng chứng
     */
    static List<Item> apply(List<Item> items, Map<String, Integer> hintCounts, String context) {
        String haystack = CandidateHintRule.fold(context);
        List<Item> checked = new ArrayList<>();
        for (Item i : items) {
            String evidence = i.evidence() == null || i.evidence().isBlank() ? null : i.evidence().strip();
            boolean found = evidence != null && haystack.contains(CandidateHintRule.fold(evidence));
            String kept = found ? evidence : null;
            checked.add(new Item(i.nodeKey(), SHOULD.equals(i.advice()) && !found ? UNSURE : i.advice(), i.reason(), kept));
        }
        List<Item> should = checked.stream().filter(i -> SHOULD.equals(i.advice())).toList();
        if (should.size() <= 1) {
            return checked;
        }
        int best = should.stream().mapToInt(i -> hintCounts.getOrDefault(i.nodeKey(), 0)).max().orElse(0);
        long withBest = should.stream().filter(i -> hintCounts.getOrDefault(i.nodeKey(), 0) == best).count();
        boolean keepBest = best > 0 && withBest == 1;
        List<Item> result = new ArrayList<>();
        for (Item i : checked) {
            boolean demote = SHOULD.equals(i.advice()) && !(keepBest && hintCounts.getOrDefault(i.nodeKey(), 0) == best);
            result.add(demote ? new Item(i.nodeKey(), UNSURE, i.reason(), i.evidence()) : i);
        }
        return result;
    }
}
