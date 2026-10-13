package com.ctv.flowtracer.ir.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;

/**
 * Nội dung tab Kịch bản: các actor tham gia và danh sách bước. Bước {@code n} khớp đúng mũi tên số {@code n} trên diagram.
 *
 * @param actors các cột của diagram theo đúng thứ tự, kèm mô tả ngắn
 * @param items bước và dòng điều kiện, theo thứ tự đọc
 */
public record Scenario(List<Actor> actors, List<Item> items) {

    public Scenario {
        actors = actors == null ? List.of() : List.copyOf(actors);
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * Một actor (cột của diagram).
     *
     * @param id id cột trên diagram
     * @param name tên theo nhãn cột, vd "Controller Danh mục"
     * @param description mô tả ngắn vai trò của actor
     */
    public record Actor(String id, String name, String description) {

        public Actor {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
        }
    }

    /**
     * Một dòng của kịch bản: bước (có {@code index}) hoặc dòng điều kiện mở nhánh (không có {@code index}, vd "Nếu … :").
     *
     * @param index số thứ tự bước = số mũi tên trên diagram; null với dòng điều kiện
     * @param text câu mô tả (bước) hoặc điều kiện (dòng điều kiện)
     * @param depth độ lồng của nhánh chứa dòng này (0 = luồng chính)
     * @param unresolved true nếu bước tới đích chưa xác định
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public record Item(Integer index, String text, int depth, boolean unresolved) {

        public Item {
            Objects.requireNonNull(text, "text");
        }

        public boolean isCondition() {
            return index == null;
        }
    }
}
