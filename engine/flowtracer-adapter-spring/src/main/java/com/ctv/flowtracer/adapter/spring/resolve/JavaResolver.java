package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;

/** PHA 2: ghép dữ kiện của cả Source → IrFragment (đã sắp xếp ổn định). */
public final class JavaResolver {

    public IrFragment resolve(ResolveContext ctx, List<JavaFileFacts> facts) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
