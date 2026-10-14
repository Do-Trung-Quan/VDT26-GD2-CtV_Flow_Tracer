package com.ctv.flowtracer.app.ai.model;

import java.util.Map;
import java.util.UUID;

/**
 * Một lần nhờ AI, do package chức năng dựng rồi giao cho {@code AiGateway}. Prompt ở đây là bản CHƯA che secret; cổng AI che
 * trước khi gửi và chỉ lưu mã băm.
 *
 * @param workspaceId Workspace sở hữu bản lưu
 * @param purpose mục đích
 * @param targetKey khóa đối tượng được diễn giải (node key, mã luồng…); cùng mục đích + đối tượng + đầu vào thì dùng lại bản lưu
 * @param promptVersion phiên bản prompt, vd {@code purpose-text.v1}; là một phần của khóa cache
 * @param system chỉ dẫn hệ thống
 * @param user nội dung yêu cầu (đã gồm dữ kiện của đối tượng)
 * @param jsonSchema schema đầu ra mong muốn (đưa vào prompt; kiểm tra thật nằm ở {@code validator})
 * @param maxOutputTokens giới hạn token đầu ra
 * @param validator kiểm tra và làm sạch đầu ra; ném {@link AiRejectedException} nếu không đạt
 */
public record AiCall(
        UUID workspaceId,
        AiPurpose purpose,
        String targetKey,
        String promptVersion,
        String system,
        String user,
        String jsonSchema,
        int maxOutputTokens,
        OutputValidator validator) {

    @FunctionalInterface
    public interface OutputValidator {

        /** @return nội dung đã kiểm tra, sẽ được lưu và trả về */
        Map<String, Object> validate(Map<String, Object> raw) throws AiRejectedException;
    }
}
