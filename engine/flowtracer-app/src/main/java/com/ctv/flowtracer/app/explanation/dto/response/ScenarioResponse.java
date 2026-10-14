package com.ctv.flowtracer.app.explanation.dto.response;

import com.ctv.flowtracer.ir.scenario.Scenario;
import java.util.List;

/**
 * Nội dung tab Kịch bản của một luồng đã dò (API F3). Bước {@code n} khớp đúng mũi tên số {@code n} trên diagram.
 *
 * @param traceId mã luồng đã dò (D1)
 * @param title tên chức năng, lấy theo thành phần xuất phát
 * @param source nguồn lời kể: {@code FRAMEWORK} (khung câu tất định); {@code AI} từ Giai đoạn 7
 * @param actors các actor tham gia, theo thứ tự cột của diagram
 * @param items bước và dòng điều kiện, theo thứ tự đọc
 * @param ai trạng thái lời kể do AI viết (xem {@link AiInfoResponse})
 */
public record ScenarioResponse(
        String traceId,
        String title,
        String source,
        List<ActorResponse> actors,
        List<ItemResponse> items,
        AiInfoResponse ai) {

    /**
     * Một actor (cột của diagram).
     *
     * @param id id cột trên diagram
     * @param name tên theo nhãn cột
     * @param description mô tả ngắn vai trò
     */
    public record ActorResponse(String id, String name, String description) {}

    /**
     * Một dòng của kịch bản.
     *
     * @param index số thứ tự bước = số mũi tên; null với dòng điều kiện ("Nếu … :")
     * @param text câu mô tả hoặc điều kiện
     * @param depth độ lồng của nhánh chứa dòng này (0 = luồng chính)
     * @param unresolved true nếu bước tới đích chưa xác định
     */
    public record ItemResponse(Integer index, String text, int depth, boolean unresolved) {}

    public static ScenarioResponse of(String traceId, String title, Scenario scenario) {
        return new ScenarioResponse(
                traceId,
                title,
                "FRAMEWORK",
                scenario.actors().stream().map(a -> new ActorResponse(a.id(), a.name(), a.description())).toList(),
                scenario.items().stream().map(i -> new ItemResponse(i.index(), i.text(), i.depth(), i.unresolved())).toList(),
                null);
    }

    /** Bản có trạng thái AI; không có lời kể AI ({@code polished} rỗng) thì giữ khung. */
    public ScenarioResponse with(AiInfoResponse info, java.util.Map<Integer, String> polished) {
        if (polished == null || polished.isEmpty()) {
            return new ScenarioResponse(traceId, title, source, actors, items, info);
        }
        return new ScenarioResponse(
                traceId,
                title,
                "AI",
                actors,
                items.stream().map(i -> i.index() != null && polished.containsKey(i.index())
                        ? new ItemResponse(i.index(), polished.get(i.index()), i.depth(), i.unresolved())
                        : i).toList(),
                info);
    }
}
