package com.ctv.flowtracer.adapter.spring;

import com.ctv.flowtracer.ir.LinkRule;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;

/** Luật Linker phía BE tự phát hiện (context-path từ application*.yml/.properties). */
public final class SpringLinkRules {

    public List<LinkRule> detect(ResolveContext ctx) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
