package com.ctv.flowtracer.app.linking.dto.response;

import com.ctv.flowtracer.app.linking.entity.OverrideDecision;
import com.ctv.flowtracer.ir.EdgeKind;
import java.time.Instant;
import java.util.UUID;

/**
 * Một liên kết đã chốt tay (API E3–E4).
 *
 * @param id id của quyết định (dùng để bỏ, API E5)
 * @param fromNodeKey key của thành phần gọi
 * @param fromName tên của thành phần gọi; null nếu không còn trong kết quả quét gần nhất
 * @param toNodeKey key của đích (null khi loại bỏ cả cạnh)
 * @param toName tên của đích; null nếu không có hoặc không còn trong kết quả quét
 * @param edgeKind loại liên kết
 * @param decision {@code PIN} / {@code REJECT}
 * @param note ghi chú
 * @param createdAt thời điểm chốt
 * @param stale true nếu thành phần gọi hoặc đích không còn trong kết quả quét gần nhất (quyết định vẫn được giữ)
 */
public record LinkOverrideResponse(
        UUID id,
        String fromNodeKey,
        String fromName,
        String toNodeKey,
        String toName,
        EdgeKind edgeKind,
        OverrideDecision decision,
        String note,
        Instant createdAt,
        boolean stale) {}
