package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;
import java.util.Map;

/** Call site → method đích; không xác định → UNRESOLVED kèm reason. */
public final class CallResolver {

    public List<IrEdge> resolveCalls(List<JavaFileFacts> facts, ClassIndex index) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
