package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;
import java.util.Map;

/** Khóa thông báo trong throw → câu tiếng Việt (messages_vi.properties). */
public final class MessageResolver {

    public Map<String, String> loadMessages(ResolveContext ctx) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
