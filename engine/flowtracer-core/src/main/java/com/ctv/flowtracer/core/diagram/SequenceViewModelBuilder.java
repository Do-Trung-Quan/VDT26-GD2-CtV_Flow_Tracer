package com.ctv.flowtracer.core.diagram;

import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.trace.Participant;
import com.ctv.flowtracer.ir.trace.Trace;
import com.ctv.flowtracer.ir.trace.TraceBlock;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.ArrayList;
import java.util.List;

/** Trace → mô hình hiển thị (cột, mũi tên, khung điều kiện, kiểu nét theo mức tin cậy). Toạ độ tất định. */
public final class SequenceViewModelBuilder {

    static final int MARGIN = 40;
    static final int COLUMN_WIDTH = 210;
    static final int HEADER_HEIGHT = 56;
    static final int ROW_HEIGHT = 44;
    static final int FRAME_PADDING = 18;

    public SequenceViewModel build(Trace trace) {
        var columns = new ArrayList<SequenceViewModel.Column>();
        List<Participant> participants = trace.participants();
        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);
            columns.add(new SequenceViewModel.Column(p.id(), p.label(), p.role().name(), MARGIN + COLUMN_WIDTH / 2 + i * COLUMN_WIDTH));
        }
        var arrows = new ArrayList<SequenceViewModel.Arrow>();
        for (TraceStep s : trace.steps()) {
            arrows.add(new SequenceViewModel.Arrow(
                    s.index(),
                    s.from(),
                    s.to(),
                    s.label(),
                    rowY(s.index()),
                    s.confidence().name(),
                    s.returning() || s.confidence() != Confidence.CERTAIN,
                    s.returning(),
                    s.error(),
                    s.pinned()));
        }
        var frames = new ArrayList<SequenceViewModel.Frame>();
        for (TraceBlock b : trace.blocks()) {
            int depth = depthOf(trace.blocks(), b);
            frames.add(new SequenceViewModel.Frame(b.type().name(), b.label(), rowY(b.fromStep()) - FRAME_PADDING, rowY(b.toStep()) + FRAME_PADDING, depth));
            for (TraceBlock.Branch branch : b.elseBranches()) {
                int y = rowY(branch.fromStep()) - FRAME_PADDING;
                frames.add(new SequenceViewModel.Frame("ELSE", branch.label(), y, y, depth));
            }
        }
        int width = MARGIN * 2 + Math.max(1, participants.size()) * COLUMN_WIDTH;
        int height = HEADER_HEIGHT * 2 + (trace.steps().size() + 1) * ROW_HEIGHT;
        return new SequenceViewModel(width, height, columns, arrows, frames);
    }

    private static int rowY(int stepIndex) {
        return HEADER_HEIGHT + stepIndex * ROW_HEIGHT;
    }

    /** Số khối bao ngoài khối này (0 với khối ngoài cùng). */
    private static int depthOf(List<TraceBlock> blocks, TraceBlock block) {
        int depth = 0;
        for (TraceBlock other : blocks) {
            if (other != block && other.fromStep() <= block.fromStep() && other.toStep() >= block.toStep()
                    && (other.fromStep() < block.fromStep() || other.toStep() > block.toStep())) {
                depth++;
            }
        }
        return depth;
    }
}
