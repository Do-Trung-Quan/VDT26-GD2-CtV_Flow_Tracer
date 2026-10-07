package com.ctv.flowtracer.adapter.spring.config;

import com.ctv.flowtracer.ir.LinkRule;
import java.util.List;

/** Luật "method X của class Y là điểm thực thi truy vấn" — cho thư viện truy vấn chỉ có dạng jar. */
public final class QuerySinkRules {

    private final List<LinkRule> rules;

    public QuerySinkRules(List<LinkRule> manualRules) {
        this.rules = manualRules.stream().filter(r -> r.type() == LinkRule.Type.QUERY_SINK).toList();
    }

    /** @param methodRef dạng {@code FQN#method} */
    public boolean isQuerySink(String methodRef) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }

    public List<LinkRule> rules() {
        return rules;
    }
}
