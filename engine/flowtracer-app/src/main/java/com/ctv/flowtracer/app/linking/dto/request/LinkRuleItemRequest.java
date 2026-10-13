package com.ctv.flowtracer.app.linking.dto.request;

import com.ctv.flowtracer.ir.LinkRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Một luật liên kết thủ công trong {@link UpdateLinkRulesRequest}.
 *
 * @param type {@code PREFIX} (thay tiền tố URL phía Frontend), {@code REWRITE} (biểu thức chính quy như pathRewrite của
 *     proxy), {@code CONTEXT_PATH} (tiền tố thêm vào API Backend chưa có context-path), {@code QUERY_SINK} (hàm thư viện là
 *     điểm thực thi truy vấn, dạng {@code FQN#tênHàm}; có hiệu lực từ lần quét sau)
 * @param pattern mẫu cần khớp
 * @param replacement giá trị thay thế (PREFIX / REWRITE; bỏ trống = xóa phần khớp)
 * @param description mô tả ngắn (tùy chọn)
 */
public record LinkRuleItemRequest(
        @NotNull(message = "Cần chọn loại luật")
        LinkRule.Type type,

        @NotBlank(message = "Mẫu của luật không được để trống")
        @Size(max = 300, message = "Mẫu của luật tối đa 300 ký tự")
        String pattern,

        @Size(max = 300, message = "Giá trị thay thế tối đa 300 ký tự")
        String replacement,

        @Size(max = 300, message = "Mô tả luật tối đa 300 ký tự")
        String description) {}
