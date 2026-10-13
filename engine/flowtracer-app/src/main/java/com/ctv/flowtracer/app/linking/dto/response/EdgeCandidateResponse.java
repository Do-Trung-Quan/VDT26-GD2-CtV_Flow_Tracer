package com.ctv.flowtracer.app.linking.dto.response;

import com.ctv.flowtracer.core.linker.CandidateScorer;

/**
 * Một ứng viên đích của liên kết chưa chắc chắn. Không có điểm số: điểm không phải xác suất đúng nên chỉ trả mức khớp.
 *
 * @param node thành phần ứng viên; null nếu không còn trong kết quả quét
 * @param nodeKey key của ứng viên
 * @param level mức khớp {@code HIGH} / {@code MEDIUM} / {@code LOW}
 * @param levelLabel nhãn mức khớp: Cao / Trung bình / Thấp
 * @param reason vì sao là ứng viên (vd "khớp đường dẫn có biến, áp 1 luật")
 * @param current true nếu là đích hiện tại của liên kết
 */
public record EdgeCandidateResponse(
        EdgeNodeResponse node,
        String nodeKey,
        CandidateScorer.Level level,
        String levelLabel,
        String reason,
        boolean current) {}
