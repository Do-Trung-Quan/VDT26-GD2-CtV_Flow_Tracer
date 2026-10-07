package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.trace.TraceBlock;
import com.ctv.flowtracer.ir.trace.TraceStep;
import java.util.List;

/** Gom guard (if/try) và throw thành khối alt/opt trên sequence. */
public final class BlockBuilder {

    public List<TraceBlock> build(List<TraceStep> steps, List<IrEdge> sourceEdges) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
