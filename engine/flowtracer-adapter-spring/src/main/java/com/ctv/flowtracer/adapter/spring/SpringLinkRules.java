package com.ctv.flowtracer.adapter.spring;

import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.LinkRule;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.List;
import java.util.TreeSet;

/**
 * Luật Linker phía BE tự phát hiện: context-path ({@code server.servlet.context-path} trong application*.yml/.properties).
 * Đường dẫn endpoint đã được cộng context-path khi phân tích, nên luật này chỉ để người dùng xem tool đã dùng gì.
 */
public final class SpringLinkRules {

    public List<LinkRule> detect(List<IrNode> endpoints) {
        var paths = new TreeSet<String>();
        endpoints.stream()
                .filter(n -> n.kind() == NodeKind.API_ENDPOINT)
                .map(n -> n.attrs().get("contextPath"))
                .filter(v -> v instanceof String s && !s.isBlank())
                .forEach(v -> paths.add((String) v));
        return paths.stream()
                .map(p -> new LinkRule(LinkRule.Type.CONTEXT_PATH, p, null, LinkRule.Origin.AUTO,
                        "server.servlet.context-path trong cấu hình Spring (đã cộng vào đường dẫn API)"))
                .toList();
    }
}
