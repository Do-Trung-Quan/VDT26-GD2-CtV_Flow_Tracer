package com.ctv.flowtracer.core.diagram;

import java.util.List;

/**
 * Mô hình hiển thị sequence diagram (dùng chung cho SVG phía server): toạ độ đã tính sẵn, đơn vị pixel.
 *
 * @param width chiều rộng toàn hình
 * @param height chiều cao toàn hình
 */
public record SequenceViewModel(int width, int height, List<Column> columns, List<Arrow> arrows, List<Frame> frames) {

    public SequenceViewModel {
        columns = columns == null ? List.of() : List.copyOf(columns);
        arrows = arrows == null ? List.of() : List.copyOf(arrows);
        frames = frames == null ? List.of() : List.copyOf(frames);
    }

    /**
     * @param role vai trò cột (USER, CONTROLLER, SERVICE, REPOSITORY, DATABASE…)
     * @param x toạ độ tâm của đường đời (lifeline)
     */
    public record Column(String participantId, String label, String role, int x) {}

    /**
     * @param confidence mức tin cậy ({@code CERTAIN}, {@code INFERRED}, {@code UNRESOLVED}…)
     * @param dashed nét đứt khi là chiều trả về hoặc mức tin cậy không phải CERTAIN
     */
    public record Arrow(
            int stepIndex,
            String fromId,
            String toId,
            String label,
            int y,
            String confidence,
            boolean dashed,
            boolean returning,
            boolean error,
            boolean pinned) {

        public boolean isSelf() {
            return fromId.equals(toId);
        }
    }

    /**
     * Khung điều kiện hoặc vạch ngăn nhánh.
     *
     * @param type {@code ALT}, {@code OPT}, {@code LOOP} hoặc {@code ELSE} (vạch ngăn nhánh, {@code yStart == yEnd})
     */
    public record Frame(String type, String label, int yStart, int yEnd, int depth) {}
}
