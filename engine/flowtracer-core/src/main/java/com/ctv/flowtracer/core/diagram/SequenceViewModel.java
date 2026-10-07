package com.ctv.flowtracer.core.diagram;

import java.util.List;

/** Mô hình hiển thị sequence diagram (dùng chung cho SVG phía server). */
public record SequenceViewModel(List<Column> columns, List<Arrow> arrows, List<Frame> frames) {

    public record Column(String participantId, String label, int x) {}

    /** @param dashed nét đứt khi mức tin cậy không phải CERTAIN */
    public record Arrow(int stepIndex, String fromId, String toId, String label, int y, boolean dashed, boolean returning) {}

    public record Frame(String type, String label, int yStart, int yEnd) {}
}
