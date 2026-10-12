package com.ctv.flowtracer.core.diagram;

import java.util.HashMap;
import java.util.Map;

/**
 * Mô hình hiển thị → SVG tĩnh (chức năng Xuất diagram). Quy ước nét như giao diện: liền = chắc chắn, đứt vàng = suy luận,
 * đứt đỏ = chưa xác định, đứt xanh lá = chốt tay; chiều trả về luôn nét đứt. Kết quả tất định, không phụ thuộc phông
 * chữ ngoài.
 */
public final class SvgExporter {

    private static final int BOX_WIDTH = 170;
    private static final int BOX_HEIGHT = 36;
    private static final double CHAR_WIDTH = 6.4;

    public String export(SequenceViewModel model) {
        Map<String, Integer> x = new HashMap<>();
        model.columns().forEach(c -> x.put(c.participantId(), c.x()));
        int bottom = model.height() - SequenceViewModelBuilder.HEADER_HEIGHT;
        var svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(model.width()).append("\" height=\"")
                .append(model.height()).append("\" viewBox=\"0 0 ").append(model.width()).append(' ').append(model.height())
                .append("\" font-family=\"Segoe UI, Arial, sans-serif\" font-size=\"12\">\n");
        svg.append("<defs><marker id=\"arrow\" viewBox=\"0 0 10 10\" refX=\"9\" refY=\"5\" markerWidth=\"7\" markerHeight=\"7\" orient=\"auto-start-reverse\">"
                + "<path d=\"M0,0 L10,5 L0,10 z\" fill=\"context-stroke\"/></marker></defs>\n");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n");

        for (SequenceViewModel.Frame f : model.frames()) {
            frame(svg, model, f);
        }
        for (SequenceViewModel.Column c : model.columns()) {
            svg.append("<line x1=\"").append(c.x()).append("\" y1=\"").append(SequenceViewModelBuilder.HEADER_HEIGHT)
                    .append("\" x2=\"").append(c.x()).append("\" y2=\"").append(bottom)
                    .append("\" stroke=\"#9CA3AF\" stroke-dasharray=\"4 4\"/>\n");
            box(svg, c, 10);
            box(svg, c, bottom);
        }
        for (SequenceViewModel.Arrow a : model.arrows()) {
            arrow(svg, a, x.get(a.fromId()), x.get(a.toId()));
        }
        return svg.append("</svg>\n").toString();
    }

    private static void box(StringBuilder svg, SequenceViewModel.Column c, int y) {
        boolean user = c.role().equals("USER");
        boolean database = c.role().equals("DATABASE");
        svg.append("<rect x=\"").append(c.x() - BOX_WIDTH / 2).append("\" y=\"").append(y).append("\" width=\"").append(BOX_WIDTH)
                .append("\" height=\"").append(BOX_HEIGHT).append("\" rx=\"").append(database ? 16 : user ? 18 : 6)
                .append("\" fill=\"").append(user ? "#F3F4F6" : database ? "#EFF6FF" : "#FEF2F2")
                .append("\" stroke=\"#374151\"/>\n");
        svg.append("<text x=\"").append(c.x()).append("\" y=\"").append(y + BOX_HEIGHT / 2 + 4)
                .append("\" text-anchor=\"middle\" font-weight=\"600\" fill=\"#111827\">")
                .append(escape(fit(c.label(), BOX_WIDTH - 12))).append("</text>\n");
    }

    private static void frame(StringBuilder svg, SequenceViewModel model, SequenceViewModel.Frame f) {
        int inset = 12 + f.depth() * 8;
        int left = SequenceViewModelBuilder.MARGIN / 2 + inset;
        int width = model.width() - 2 * left;
        if (f.type().equals("ELSE")) {
            svg.append("<line x1=\"").append(left).append("\" y1=\"").append(f.yStart()).append("\" x2=\"").append(left + width)
                    .append("\" y2=\"").append(f.yStart()).append("\" stroke=\"#6B7280\" stroke-dasharray=\"6 3\"/>\n");
            svg.append("<text x=\"").append(left + 8).append("\" y=\"").append(f.yStart() + 14)
                    .append("\" fill=\"#374151\" font-weight=\"600\">[").append(escape(f.label())).append("]</text>\n");
            return;
        }
        svg.append("<rect x=\"").append(left).append("\" y=\"").append(f.yStart()).append("\" width=\"").append(width)
                .append("\" height=\"").append(f.yEnd() - f.yStart()).append("\" fill=\"none\" stroke=\"#6B7280\"/>\n");
        String tag = f.type().toLowerCase(java.util.Locale.ROOT);
        svg.append("<rect x=\"").append(left).append("\" y=\"").append(f.yStart()).append("\" width=\"").append(46)
                .append("\" height=\"18\" fill=\"#E5E7EB\" stroke=\"#6B7280\"/>\n");
        svg.append("<text x=\"").append(left + 6).append("\" y=\"").append(f.yStart() + 13)
                .append("\" font-weight=\"700\" fill=\"#111827\">").append(tag).append("</text>\n");
        svg.append("<text x=\"").append(left + 54).append("\" y=\"").append(f.yStart() + 13).append("\" fill=\"#374151\">[")
                .append(escape(fit(f.label(), width - 70))).append("]</text>\n");
    }

    private static void arrow(StringBuilder svg, SequenceViewModel.Arrow a, int fromX, int toX) {
        String color = a.pinned() ? "#15803D" : switch (a.confidence()) {
            case "INFERRED" -> "#B45309";
            case "UNRESOLVED" -> "#DC2626";
            default -> a.error() ? "#B91C1C" : "#1F2937";
        };
        String dash = a.dashed() ? " stroke-dasharray=\"6 4\"" : "";
        String number = a.stepIndex() + ". ";
        if (a.isSelf()) {
            int w = 36;
            svg.append("<path d=\"M").append(fromX).append(' ').append(a.y() - 8).append(" h").append(w).append(" v16 h-").append(w)
                    .append("\" fill=\"none\" stroke=\"").append(color).append("\"").append(dash).append(" marker-end=\"url(#arrow)\"/>\n");
            svg.append("<text x=\"").append(fromX + w + 6).append("\" y=\"").append(a.y() + 4).append("\" fill=\"").append(color)
                    .append("\">").append(escape(fit(number + a.label(), 230))).append("</text>\n");
            return;
        }
        svg.append("<line x1=\"").append(fromX).append("\" y1=\"").append(a.y()).append("\" x2=\"").append(toX).append("\" y2=\"")
                .append(a.y()).append("\" stroke=\"").append(color).append("\"").append(dash).append(" marker-end=\"url(#arrow)\"/>\n");
        int mid = (fromX + toX) / 2;
        svg.append("<text x=\"").append(mid).append("\" y=\"").append(a.y() - 6).append("\" text-anchor=\"middle\" fill=\"").append(color)
                .append("\">").append(escape(fit(number + a.label(), Math.abs(toX - fromX) - 12))).append("</text>\n");
    }

    /** Cắt chữ cho vừa bề rộng (ước lượng), thêm "…" nếu bị cắt. */
    private static String fit(String text, int maxWidth) {
        int maxChars = Math.max(4, (int) (maxWidth / CHAR_WIDTH));
        return text.length() <= maxChars ? text : text.substring(0, maxChars - 1) + "…";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
