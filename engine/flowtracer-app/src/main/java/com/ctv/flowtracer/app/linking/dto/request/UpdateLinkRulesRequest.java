package com.ctv.flowtracer.app.linking.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Yêu cầu cập nhật luật liên kết thủ công (API E7); thay toàn bộ danh sách cũ. Luật tự phát hiện không sửa được (chúng được
 * đọc lại mỗi lần quét).
 *
 * @param manual danh sách luật thủ công, theo thứ tự ưu tiên (trùng loại + mẫu + thay thế thì giữ mục đầu)
 */
public record UpdateLinkRulesRequest(
        @NotNull(message = "Danh sách luật là bắt buộc (có thể rỗng)")
        @Size(max = 100, message = "Tối đa 100 luật thủ công")
        List<@NotNull(message = "Luật không được rỗng") @Valid LinkRuleItemRequest> manual) {}
