package com.ctv.flowtracer.app.linking.dto.response;

import com.ctv.flowtracer.core.linker.CandidateScorer;
import java.util.List;

/**
 * Một ứng viên đích của liên kết chưa chắc chắn. Không có điểm số: điểm không phải xác suất đúng nên chỉ trả mức khớp.
 *
 * @param node thành phần ứng viên; null nếu không còn trong kết quả quét
 * @param nodeKey key của ứng viên
 * @param level mức khớp {@code HIGH} / {@code MEDIUM} / {@code LOW}
 * @param levelLabel nhãn mức khớp: Cao / Trung bình / Thấp
 * @param reason vì sao là ứng viên (vd "khớp đường dẫn có biến, áp 1 luật")
 * @param current true nếu là đích hiện tại của liên kết
 * @param hints các từ trong chuỗi gọi (tên hàm, chuỗi hằng truyền vào) trùng với đường dẫn của ứng viên và phân biệt được nó với các
 *     ứng viên khác — tín hiệu so khớp tên tất định, không cần AI; rỗng nếu không có hoặc không biết thành phần xuất phát của luồng
 */
public record EdgeCandidateResponse(
        EdgeNodeResponse node,
        String nodeKey,
        CandidateScorer.Level level,
        String levelLabel,
        String reason,
        boolean current,
        List<String> hints) {}
