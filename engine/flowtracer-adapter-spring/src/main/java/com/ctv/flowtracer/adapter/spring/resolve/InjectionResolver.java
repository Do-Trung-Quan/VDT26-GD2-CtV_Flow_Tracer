package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;
import java.util.Map;

/** Phụ thuộc được tiêm: field final + @RequiredArgsConstructor (Lombok), @Autowired, tham số constructor. */
public final class InjectionResolver {

    public Map<String, String> injectedTypes(JavaFileFacts.TypeFact type, ClassIndex index) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
