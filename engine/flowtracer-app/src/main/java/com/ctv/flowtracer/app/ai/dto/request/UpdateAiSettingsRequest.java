package com.ctv.flowtracer.app.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** Bật / tắt từng mục đích dùng AI (API I2). Không có provider hay khóa API: những thứ đó chỉ nằm trong biến môi trường. */
public record UpdateAiSettingsRequest(
        /** Mục đích ({@code PURPOSE_TEXT}, {@code SCENARIO_POLISH}…) → bật (true) / tắt (false); chỉ cần gửi mục đích muốn đổi. */
        @NotEmpty(message = "Danh sách mục đích không được để trống")
                Map<@NotBlank(message = "Tên mục đích không được để trống") String, @NotNull(message = "Trạng thái bật/tắt không được để trống") Boolean> purposes) {}
