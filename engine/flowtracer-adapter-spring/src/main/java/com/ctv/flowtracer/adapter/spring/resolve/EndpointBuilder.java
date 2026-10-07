package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;
import java.util.Map;

/** API_ENDPOINT + HANDLED_BY: ghép prefix class + method + context-path; bóc ResponseEntity/ApiResponse<T>. */
public final class EndpointBuilder {

    public List<IrNode> buildEndpoints(List<JavaFileFacts> facts, String contextPath) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
