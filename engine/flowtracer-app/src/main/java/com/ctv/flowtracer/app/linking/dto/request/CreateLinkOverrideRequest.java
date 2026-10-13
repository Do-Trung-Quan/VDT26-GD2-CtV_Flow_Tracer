package com.ctv.flowtracer.app.linking.dto.request;

import com.ctv.flowtracer.app.linking.entity.OverrideDecision;
import com.ctv.flowtracer.ir.EdgeKind;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu chốt tay một liên kết (API E4).
 *
 * @param fromNodeKey key của thành phần gọi (lời gọi HTTP, hoặc hàm có lời gọi chưa xác định)
 * @param toNodeKey key của đích được chọn; bắt buộc khi ghim, tùy chọn khi loại bỏ (bỏ trống = bỏ cả cạnh)
 * @param edgeKind loại liên kết: {@code HTTP_REQUEST} (lời gọi HTTP → API) hoặc {@code CALLS} (hàm → hàm)
 * @param decision {@code PIN} (ghim đích này) hoặc {@code REJECT} (liên kết này sai)
 * @param note ghi chú của người dùng (tùy chọn)
 */
public record CreateLinkOverrideRequest(
        @NotBlank(message = "Cần chọn thành phần gọi")
        @Size(max = 1000, message = "Mã thành phần gọi tối đa 1000 ký tự")
        String fromNodeKey,

        @Size(max = 1000, message = "Mã thành phần đích tối đa 1000 ký tự")
        String toNodeKey,

        @NotNull(message = "Cần chọn loại liên kết")
        EdgeKind edgeKind,

        @NotNull(message = "Cần chọn quyết định: ghim hoặc loại bỏ")
        OverrideDecision decision,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note) {

    /** Ghim phải có đích. Chỉ để kiểm tra dữ liệu, không phải một trường của request. */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Ghim liên kết cần chọn thành phần đích")
    public boolean isTargetPresentForPin() {
        return decision != OverrideDecision.PIN || toNodeKey != null && !toNodeKey.isBlank();
    }
}
