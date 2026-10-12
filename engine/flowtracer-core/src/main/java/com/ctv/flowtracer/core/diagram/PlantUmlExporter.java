package com.ctv.flowtracer.core.diagram;

import com.ctv.flowtracer.ir.trace.Participant;
import com.ctv.flowtracer.ir.trace.Trace;
import com.ctv.flowtracer.ir.trace.TraceBlock;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trace → mã PlantUML sequence (chức năng Xuất diagram): {@code skinparam style strictuml}, {@code autonumber},
 * {@code actor} cho Client/User, {@code database} cho Database, {@code activate}/{@code deactivate}, chiều trả về
 * {@code -->}, khối {@code alt}/{@code else}/{@code opt}/{@code loop}. Số tự đánh của {@code autonumber} trùng số bước
 * vì mọi mũi tên (kể cả chiều trả về) đều là một bước.
 *
 * <p>Chiều trả về do lỗi lan lên nằm trong nhánh đầu của khối {@code alt} nên KHÔNG {@code deactivate} — nhánh {@code else}
 * vẫn chạy tiếp trên cùng các cột đó.
 */
public final class PlantUmlExporter {

    private static final String INDENT = "  ";

    public String export(Trace trace) {
        Map<String, String> alias = aliases(trace.participants());
        var out = new StringBuilder();
        out.append("@startuml ").append(title(trace)).append('\n');
        out.append("autonumber\n");
        out.append("skinparam style strictuml\n");
        out.append("skinparam responseMessageBelowArrow true\n");
        for (Participant p : trace.participants()) {
            String keyword = switch (p.role()) {
                case USER -> "actor";
                case DATABASE -> "database";
                default -> "participant";
            };
            out.append(keyword).append(" \"").append(clean(p.label())).append("\" as ").append(alias.get(p.id())).append('\n');
        }
        out.append('\n');

        Map<String, Integer> active = new HashMap<>();
        var activationOrder = new ArrayList<String>();
        int depth = 0;
        for (TraceStep step : trace.steps()) {
            for (TraceBlock b : trace.blocks()) {
                for (TraceBlock.Branch branch : b.elseBranches()) {
                    if (branch.fromStep() == step.index()) {
                        out.append(INDENT.repeat(Math.max(0, depthOf(trace, b))));
                        out.append("else ").append(clean(branch.label())).append('\n');
                    }
                }
            }
            for (TraceBlock b : trace.blocks()) {
                if (b.fromStep() == step.index()) {
                    out.append(INDENT.repeat(depth)).append(b.type().name().toLowerCase(Locale.ROOT)).append(' ')
                            .append(clean(b.label())).append('\n');
                    depth++;
                }
            }
            String pad = INDENT.repeat(depth);
            out.append(pad).append(alias.get(step.from())).append(step.returning() ? " --> " : " -> ")
                    .append(alias.get(step.to())).append(": ").append(clean(step.label())).append('\n');
            if (step.returning() && !step.error()) {
                if (active.getOrDefault(step.from(), 0) > 0) {
                    out.append(pad).append("deactivate ").append(alias.get(step.from())).append('\n');
                    active.merge(step.from(), -1, Integer::sum);
                }
            } else if (!step.returning() && !step.isSelf()) {
                out.append(pad).append("activate ").append(alias.get(step.to())).append('\n');
                active.merge(step.to(), 1, Integer::sum);
                activationOrder.add(step.to());
            }
            List<TraceBlock> closing = new ArrayList<>(trace.blocks().stream().filter(b -> b.toStep() == step.index()).toList());
            for (int i = closing.size() - 1; i >= 0; i--) {
                depth--;
                out.append(INDENT.repeat(depth)).append("end\n");
            }
        }
        for (int i = activationOrder.size() - 1; i >= 0; i--) {
            String id = activationOrder.get(i);
            if (active.getOrDefault(id, 0) > 0) {
                out.append("deactivate ").append(alias.get(id)).append('\n');
                active.merge(id, -1, Integer::sum);
            }
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /** Số khối bao ngoài khối {@code b} (độ thụt lề của dòng {@code else}). */
    private static int depthOf(Trace trace, TraceBlock b) {
        int depth = 0;
        for (TraceBlock other : trace.blocks()) {
            if (other != b && other.fromStep() <= b.fromStep() && other.toStep() >= b.toStep()
                    && (other.fromStep() < b.fromStep() || other.toStep() > b.toStep())) {
                depth++;
            }
        }
        return depth;
    }

    private static Map<String, String> aliases(List<Participant> participants) {
        Map<String, String> alias = new HashMap<>();
        for (Participant p : participants) {
            alias.put(p.id(), p.id().equals("user") ? "User" : p.id().toUpperCase(Locale.ROOT));
        }
        return alias;
    }

    /** Tên sơ đồ: phần sau {@code java:endpoint:} / {@code ng:element:}…, ký tự lạ thành dấu gạch dưới. */
    private static String title(Trace trace) {
        String key = trace.startKey();
        int second = key.indexOf(':', key.indexOf(':') + 1);
        String name = second < 0 ? key : key.substring(second + 1);
        return name.replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", "");
    }

    private static String clean(String text) {
        return text.replace('\n', ' ').replace('\r', ' ').replace("\"", "'");
    }
}
