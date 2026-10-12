package com.ctv.flowtracer.adapter.spring.config;

import com.ctv.flowtracer.ir.LinkRule;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Luật "method X của class Y là điểm thực thi truy vấn" — cho thư viện truy vấn chỉ có dạng jar (không có mã nguồn để
 * theo dấu). Mẫu: {@code FQN#method}; {@code *} khớp mọi ký tự, vd {@code com.acme.query.QueryExecutor#*}.
 */
public final class QuerySinkRules {

    private final List<LinkRule> rules;
    private final List<Pattern> patterns;

    public QuerySinkRules(List<LinkRule> manualRules) {
        this.rules = manualRules.stream().filter(r -> r.type() == LinkRule.Type.QUERY_SINK).toList();
        this.patterns = rules.stream().map(r -> toRegex(r.pattern())).toList();
    }

    /** @param methodRef dạng {@code FQN#method} */
    public boolean isQuerySink(String methodRef) {
        return patterns.stream().anyMatch(p -> p.matcher(methodRef).matches());
    }

    public List<LinkRule> rules() {
        return rules;
    }

    private static Pattern toRegex(String glob) {
        var regex = new StringBuilder();
        for (String part : glob.split("\\*", -1)) {
            if (regex.length() > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }
}
