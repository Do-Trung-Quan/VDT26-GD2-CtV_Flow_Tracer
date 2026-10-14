package com.ctv.flowtracer.app.explanation.dto.request;

import com.ctv.flowtracer.app.ai.model.AiPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu sinh (hoặc sinh lại) nội dung AI (API F4).
 *
 * @param purpose mục đích: {@code PURPOSE_TEXT}, {@code SCENARIO_POLISH}, {@code MOCK_DATA}, {@code ELEMENT_NAMING}
 * @param targetKey đối tượng: key của API / phần tử ({@code PURPOSE_TEXT}, {@code MOCK_DATA}), mã luồng đã dò ({@code SCENARIO_POLISH}),
 *     key của màn hình / hộp thoại ({@code ELEMENT_NAMING})
 * @param apiKey khi {@code MOCK_DATA} cho phần tử giao diện gọi nhiều API: API cần sinh; bỏ trống thì lấy API đầu tiên
 * @param force true (mặc định) = bỏ qua bản đã lưu và gọi AI lại; false = chỉ gọi khi chưa có bản lưu
 */
public record GenerateAiRequest(
        @NotNull(message = "Mục đích không được để trống") AiPurpose purpose,
        @NotBlank(message = "Đối tượng không được để trống") @Size(max = 2000, message = "Đối tượng tối đa 2000 ký tự") String targetKey,
        @Size(max = 2000, message = "Mã API tối đa 2000 ký tự") String apiKey,
        Boolean force) {}
