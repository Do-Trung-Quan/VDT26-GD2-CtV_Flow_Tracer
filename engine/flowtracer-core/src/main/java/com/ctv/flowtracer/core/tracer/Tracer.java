package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.core.graph.Subgraph;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.trace.Trace;
import java.util.ArrayList;
import java.util.List;

/**
 * CỐT LÕI: dò vết End-to-End theo {@code callOrder} từ node xuất phát. Hỗ trợ xuất phát từ một API Backend (cột Client →
 * Controller → Service → Repository → Database) và từ một phần tử giao diện KHÔNG gọi máy chủ ({@link UiOnlyTracer}: cột User
 * ↔ Frontend). Phần tử gọi API cần nối FE với BE nên làm ở Giai đoạn 5.
 *
 * <p>Tất định: cùng đồ thị và cùng yêu cầu cho cùng {@link Trace}. Không bao giờ chọn hộ đích của lời gọi chưa xác định —
 * chỉ liên kết chốt tay ({@link OverrideApplier}) mới đổi được.
 *
 * <p>Mục tiêu diagram không dài quá {@value #MAX_STEPS} bước, nhưng KHÔNG đánh đổi nghiệp vụ lấy độ ngắn: luồng đầy đủ dài
 * hơn thì rút gọn dần theo thứ tự mất ít thông tin nhất, mỗi mức ghi vào cảnh báo — (1) không vẽ lại phần bên trong của
 * lời gọi lặp lại (thông tin vẫn có ở lần gọi đầu); (2) bỏ các bước tự xử lý (chỉ mất tên hàm phụ trong cùng một lớp).
 * Không bao giờ cắt độ sâu hay bỏ nhánh lỗi nghiệp vụ: sau hai mức mà vẫn dài hơn giới hạn thì giữ nguyên và ghi rõ.
 */
public final class Tracer {

    /** Số bước tối đa của một diagram. */
    public static final int MAX_STEPS = 70;

    private final OverrideApplier overrides = new OverrideApplier();

    /**
     * @param graph đồ thị con quanh node xuất phát (đã nạp từ CSDL)
     * @return trace gồm participant, các bước (cả chiều trả về), khối alt/opt/loop và cảnh báo
     * @throws IllegalArgumentException node xuất phát không có trong đồ thị hoặc chưa hỗ trợ loại node đó
     */
    public Trace trace(Subgraph graph, TraceRequest request) {
        return trace(graph, request, MAX_STEPS);
    }

    /** Như {@link #trace(Subgraph, TraceRequest)} với giới hạn số bước khác (dùng để đo mức rút gọn). */
    public Trace trace(Subgraph graph, TraceRequest request, int maxSteps) {
        boolean element = graph.nodes().stream().anyMatch(n -> n.key().equals(request.startKey()) && n.kind() == NodeKind.UI_ELEMENT);
        if (element && !UiOnlyTracer.callsServer(graph, request.startKey())) {
            return new UiOnlyTracer().trace(graph, request);
        }
        Subgraph applied = overrides.apply(graph, request.overrides());
        int depth = request.maxDepth();
        Trace full = new TraceRun(applied, request, new TraceRun.Options(true, true, false, depth)).run();
        if (full.steps().size() <= maxSteps) {
            return full;
        }
        String prefix = "Luồng đầy đủ có " + full.steps().size() + " bước, vượt giới hạn " + maxSteps + " bước nên ";
        Trace collapsed = new TraceRun(applied, request, new TraceRun.Options(true, true, true, depth)).run();
        if (collapsed.steps().size() <= maxSteps) {
            return withNote(collapsed, prefix + "không vẽ lại phần bên trong của các lời gọi lặp lại (xem lần gọi đầu)");
        }
        Trace noSelf = new TraceRun(applied, request, new TraceRun.Options(true, false, true, depth)).run();
        String reduced = prefix + "không vẽ lại lời gọi lặp lại và bỏ các bước tự xử lý trong cùng một lớp";
        // Không cắt độ sâu và không bỏ nhánh lỗi nghiệp vụ: đó là nghiệp vụ. Còn dài hơn giới hạn thì giữ nguyên và nói rõ.
        return withNote(
                noSelf,
                noSelf.steps().size() <= maxSteps
                        ? reduced
                        : reduced + "; vẫn dài " + noSelf.steps().size() + " bước vì giữ đủ các lời gọi và nhánh lỗi nghiệp vụ");
    }

    private static Trace withNote(Trace trace, String note) {
        List<String> warnings = new ArrayList<>();
        warnings.add(note);
        warnings.addAll(trace.warnings());
        return new Trace(
                trace.traceId(), trace.startKey(), trace.participants(), trace.steps(), trace.blocks(), warnings, trace.uiOnly());
    }
}
