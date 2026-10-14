package com.ctv.flowtracer.app.linking.service;

import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrNode;
import java.util.Optional;
import java.util.Set;

/**
 * Luật cho ứng viên mà AI đề xuất THÊM khi "Nhờ AI phân tích" (CLAUDE.md §3.7): chỉ được hiện khi là node CÓ THẬT trong IR và HỢP LỆ
 * VỀ KIỂU — liên kết HTTP → endpoint (cùng phương thức HTTP, hoặc {@code ANY}) / hệ thống ngoài; liên kết lời gọi hàm → hàm. AI
 * không bao giờ tạo node hay cạnh, cũng không đổi liên kết; chỉ người dùng chốt tay mới đổi diagram.
 */
public final class AiAdviceRule {

    /**
     * @param edgeKind loại liên kết đang xem
     * @param httpMethod phương thức HTTP của lời gọi (null nếu không biết hoặc không phải HTTP)
     * @param candidate node AI đề xuất; null nếu AI đề xuất thứ không có trong IR
     * @return node hợp lệ, hoặc rỗng nếu phải bỏ
     */
    public Optional<IrNode> acceptExtra(EdgeKind edgeKind, String httpMethod, IrNode candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        Set<com.ctv.flowtracer.ir.NodeKind> allowed = LinkOverrideService.TARGET_KINDS.get(edgeKind);
        if (allowed == null || !allowed.contains(candidate.kind())) {
            return Optional.empty();
        }
        if (edgeKind == EdgeKind.HTTP_REQUEST && candidate.kind() == com.ctv.flowtracer.ir.NodeKind.API_ENDPOINT && httpMethod != null) {
            Object method = candidate.attrs().get("httpMethod");
            boolean same = method instanceof String m && (m.equalsIgnoreCase(httpMethod) || m.equalsIgnoreCase("ANY"));
            if (!same) {
                return Optional.empty();
            }
        }
        return Optional.of(candidate);
    }
}
