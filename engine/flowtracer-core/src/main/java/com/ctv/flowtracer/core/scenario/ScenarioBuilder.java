package com.ctv.flowtracer.core.scenario;

import com.ctv.flowtracer.ir.scenario.Scenario;
import com.ctv.flowtracer.ir.trace.Participant;
import com.ctv.flowtracer.ir.trace.Trace;
import com.ctv.flowtracer.ir.trace.TraceBlock;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trace → kịch bản tất định: danh sách actor (đúng các cột của diagram) và các bước "A + hành động + tới C". Bước {@code n}
 * khớp đúng mũi tên số {@code n}. Mỗi khối {@code alt} / {@code opt} / {@code loop} mở bằng một dòng điều kiện ("Nếu … :",
 * "Ngược lại:"), các bước bên trong có độ lồng tương ứng. Không gọi AI: lời kể do AI viết (Giai đoạn 7) chỉ được dùng nếu giữ
 * nguyên số bước, thứ tự và thành phần của khung này.
 */
public final class ScenarioBuilder {

    private static final String OTHERWISE_PREFIX = "ngược lại";

    public Scenario build(Trace trace) {
        Map<String, Participant> lanes = new HashMap<>();
        trace.participants().forEach(p -> lanes.put(p.id(), p));
        var actors = trace.participants().stream()
                .map(p -> new Scenario.Actor(p.id(), p.label(), describe(p)))
                .toList();

        Map<Integer, List<Row>> conditionsAt = conditionRows(trace.blocks());
        var items = new ArrayList<Scenario.Item>();
        for (TraceStep step : trace.steps()) {
            int depth = depthOf(step.index(), trace.blocks());
            for (Row condition : conditionsAt.getOrDefault(step.index(), List.of())) {
                items.add(new Scenario.Item(null, condition.text(), condition.depth(), false));
            }
            Participant from = lanes.get(step.from());
            Participant to = lanes.get(step.to());
            String text = from == null || to == null
                    ? step.label()
                    : StepTemplatesVi.sentence(step, from, to);
            boolean unresolved = step.confidence() == com.ctv.flowtracer.ir.Confidence.UNRESOLVED && !step.returning();
            items.add(new Scenario.Item(step.index(), text, depth, unresolved));
        }
        return new Scenario(actors, items);
    }

    /** Một dòng điều kiện: độ lồng và nội dung. */
    private record Row(int depth, String text) {}

    /** Dòng điều kiện đặt trước bước đầu tiên của mỗi nhánh. Khối ngoài đứng trước khối trong khi cùng mở tại một bước. */
    private static Map<Integer, List<Row>> conditionRows(List<TraceBlock> blocks) {
        Map<Integer, List<Row>> rows = new HashMap<>();
        var ordered = blocks.stream()
                .sorted(Comparator.comparingInt(TraceBlock::fromStep).thenComparing(Comparator.comparingInt(TraceBlock::toStep).reversed()))
                .toList();
        for (TraceBlock b : ordered) {
            int depth = (int) blocks.stream().filter(o -> o != b && contains(o, b)).count();
            rows.computeIfAbsent(b.fromStep(), k -> new ArrayList<>()).add(new Row(depth, opening(b)));
            for (TraceBlock.Branch branch : b.elseBranches()) {
                rows.computeIfAbsent(branch.fromStep(), k -> new ArrayList<>()).add(new Row(depth, otherwise(branch.label())));
            }
        }
        return rows;
    }

    private static boolean contains(TraceBlock outer, TraceBlock inner) {
        return outer.fromStep() <= inner.fromStep() && inner.toStep() <= outer.toStep()
                && (outer.fromStep() < inner.fromStep() || inner.toStep() < outer.toStep());
    }

    private static int depthOf(int step, List<TraceBlock> blocks) {
        return (int) blocks.stream().filter(b -> b.fromStep() <= step && step <= b.toStep()).count();
    }

    private static String opening(TraceBlock block) {
        String label = block.label() == null ? "" : block.label().strip();
        return switch (block.type()) {
            case LOOP -> "Lặp lại " + label + ":";
            default -> label.toLowerCase(Locale.ROOT).startsWith(OTHERWISE_PREFIX) ? "Ngược lại:" : "Nếu " + label + ":";
        };
    }

    private static String otherwise(String label) {
        return label != null && !label.toLowerCase(Locale.ROOT).startsWith(OTHERWISE_PREFIX) && !label.isBlank()
                ? "Nếu " + label.strip() + ":"
                : "Ngược lại:";
    }

    /** Mô tả ngắn vai trò của một cột. */
    static String describe(Participant p) {
        String text = describeLowercase(p);
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private static String describeLowercase(Participant p) {
        String label = p.label();
        return switch (p.role()) {
            case USER -> label.equals("Client") ? "nơi gọi API (ứng dụng hoặc người dùng)." : "người dùng thao tác trên giao diện.";
            case FRONTEND -> "giao diện ứng dụng web.";
            case CONTROLLER -> "tiếp nhận và điều phối yêu cầu " + business(label, "Controller") + " ở Backend.";
            case SERVICE -> "xử lý nghiệp vụ và ràng buộc của " + business(label, "Service") + ".";
            case REPOSITORY -> "truy xuất dữ liệu " + business(label, "Repository") + ".";
            case DATABASE -> label.startsWith("Bảng ") ? "bảng dữ liệu " + label.substring("Bảng ".length()) + "." : "cơ sở dữ liệu.";
            case EXTERNAL -> "hệ thống bên ngoài Workspace.";
        };
    }

    /** "Controller Danh mục" → "danh mục" (chỉ hạ chữ hoa đầu khi tên không phải tên lớp viết liền kiểu CamelCase). */
    private static String business(String label, String prefix) {
        String name = label.startsWith(prefix + " ") ? label.substring(prefix.length() + 1) : label;
        int paren = name.indexOf(" (");
        if (paren > 0) {
            name = name.substring(0, paren);
        }
        boolean camel = name.chars().skip(1).anyMatch(Character::isUpperCase);
        return camel || name.isEmpty() ? name : name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
    }
}
