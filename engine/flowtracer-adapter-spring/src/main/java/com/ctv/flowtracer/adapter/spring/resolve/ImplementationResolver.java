package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;
import java.util.Map;

/** Interface → lớp triển khai (@Service/@Repository/@Component); hơn 1 lớp → candidates. */
public final class ImplementationResolver {

    public List<String> implementationsOf(String interfaceFqn, ClassIndex index) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
