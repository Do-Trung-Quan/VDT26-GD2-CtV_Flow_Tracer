package com.ctv.flowtracer.app.tracing.dto.response;

import com.ctv.flowtracer.ir.trace.TraceBlock;
import java.util.List;

/**
 * Khối điều kiện/lặp bao một dải bước. Khối có thể lồng nhau.
 *
 * @param type {@code ALT}, {@code OPT} hoặc {@code LOOP}
 * @param label điều kiện của nhánh chính (vd {@code categoryRepository.existsByCode(code)})
 * @param fromStep số bước đầu tiên trong khối
 * @param toStep số bước cuối cùng trong khối
 * @param elseBranches các nhánh {@code else}; nhánh chính kéo dài tới trước nhánh else đầu tiên, mỗi nhánh else kéo dài
 *     tới trước nhánh kế tiếp, nhánh cuối kéo dài tới {@code toStep}
 */
public record TraceBlockResponse(
        TraceBlock.Type type, String label, int fromStep, int toStep, List<TraceBranchResponse> elseBranches) {

    public static TraceBlockResponse from(TraceBlock b) {
        return new TraceBlockResponse(
                b.type(), b.label(), b.fromStep(), b.toStep(), b.elseBranches().stream().map(TraceBranchResponse::from).toList());
    }
}
