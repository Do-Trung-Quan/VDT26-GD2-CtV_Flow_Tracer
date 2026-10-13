package com.ctv.flowtracer.core.scenario;

import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.trace.Participant;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.Locale;

/**
 * Mẫu câu tiếng Việt cho từng loại bước của tab Kịch bản: "bấm", "gửi yêu cầu … đến", "chuyển tiếp … đến", "gọi … trên",
 * "truy vấn … trên", "trả về … cho", "tự xử lý". Câu sinh tất định từ bước của diagram (nhãn mũi tên + tên cột), nên bước n của
 * kịch bản luôn khớp mũi tên số n.
 */
public final class StepTemplatesVi {

    private StepTemplatesVi() {}

    /**
     * @param step bước của diagram
     * @param from cột nguồn
     * @param to cột đích
     * @return một câu kết thúc bằng dấu chấm
     */
    public static String sentence(TraceStep step, Participant from, Participant to) {
        String a = from.label();
        String b = to.label();
        String label = step.label();
        if (step.isSelf()) {
            return a + " tự xử lý: " + label + ".";
        }
        String base;
        if (step.returning()) {
            base = returning(step, from, to, a, b, label);
        } else {
            base = forward(step, from, to, a, b, label);
        }
        return base + notes(step);
    }

    private static String returning(TraceStep step, Participant from, Participant to, String a, String b, String label) {
        if (step.confidence() == Confidence.UNRESOLVED && label.startsWith("(")) {
            return a + " trả về kết quả chưa xác định cho " + b + ".";
        }
        if (step.error()) {
            return a + " trả lỗi " + quoteIfText(label) + " cho " + b + ".";
        }
        if (from.role() == Participant.Role.FRONTEND && to.role() == Participant.Role.USER) {
            return a + " phản hồi cho " + b + ": " + label + ".";
        }
        return a + " trả về " + label + " cho " + b + ".";
    }

    private static String forward(TraceStep step, Participant from, Participant to, String a, String b, String label) {
        if (step.kind() == EdgeKind.TRIGGERS) {
            return a + " " + lowerFirst(label) + " trên " + b + ".";
        }
        if (step.kind() == EdgeKind.HTTP_REQUEST || step.kind() == EdgeKind.HANDLED_BY) {
            return a + " gửi yêu cầu " + label + " đến " + b + ".";
        }
        if (step.kind() == EdgeKind.EXECUTES || step.kind() == EdgeKind.READS || step.kind() == EdgeKind.WRITES) {
            return a + " truy vấn " + label + " trên " + b + ".";
        }
        if (from.role() == Participant.Role.CONTROLLER) {
            return a + " chuyển tiếp yêu cầu " + label + " đến " + b + ".";
        }
        return a + " gọi " + label + " trên " + b + ".";
    }

    /** Ghi chú ngắn sau câu: đích chưa xác định, liên kết chốt tay, lần gọi lặp lại. */
    private static String notes(TraceStep step) {
        var out = new StringBuilder();
        if (!step.returning() && step.confidence() == Confidence.UNRESOLVED) {
            out.append(" Chưa xác định được đích của lời gọi này.");
        } else if (step.pinned()) {
            out.append(" (liên kết đã chốt tay)");
        }
        if (step.repeatOf() != null) {
            out.append(" Phần bên trong xem ở bước ").append(step.repeatOf()).append('.');
        }
        return out.toString();
    }

    private static String quoteIfText(String label) {
        return label.contains(" ") ? "“" + label + "”" : label;
    }

    private static String lowerFirst(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
    }
}
