package com.ctv.flowtracer.core.linker;

import com.ctv.flowtracer.ir.LinkRule;
import java.util.List;

/** Áp các luật liên kết (tiền tố base URL, pathRewrite của proxy, context-path) lên URL. */
public final class LinkRuleEngine {

    /** Áp luật phía FE (PREFIX, REWRITE) lên URL thô của lời gọi HTTP. */
    public String applyFrontendRules(String rawUrl, List<LinkRule> rules) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }

    /** Áp luật phía BE (CONTEXT_PATH) lên path của endpoint. */
    public String applyBackendRules(String path, List<LinkRule> rules) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
