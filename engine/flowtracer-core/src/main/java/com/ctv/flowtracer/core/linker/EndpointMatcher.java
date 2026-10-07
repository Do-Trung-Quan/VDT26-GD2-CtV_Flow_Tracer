package com.ctv.flowtracer.core.linker;

import com.ctv.flowtracer.ir.IrNode;
import java.util.List;

/** So khớp một lời gọi HTTP với các endpoint: method phải trùng; đoạn literal ưu tiên hơn đoạn biến. */
public final class EndpointMatcher {

    /** Một endpoint khớp kèm điểm. */
    public record Match(IrNode endpoint, int score) {}

    /** @return các endpoint khớp, điểm cao nhất trước */
    public List<Match> match(UrlPattern call, List<IrNode> endpoints) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
