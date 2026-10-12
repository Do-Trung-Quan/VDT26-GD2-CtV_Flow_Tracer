package com.ctv.flowtracer.adapter.spring.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.ir.LinkRule;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuerySinkRulesTest {

    private static LinkRule sink(String pattern) {
        return new LinkRule(LinkRule.Type.QUERY_SINK, pattern, null, LinkRule.Origin.MANUAL, null);
    }

    @Test
    void matchesExactMethodAndWildcards() {
        var rules = new QuerySinkRules(List.of(sink("com.acme.query.QueryExecutor#run"), sink("com.acme.dao.*#find*")));

        assertThat(rules.isQuerySink("com.acme.query.QueryExecutor#run")).isTrue();
        assertThat(rules.isQuerySink("com.acme.query.QueryExecutor#runAll")).isFalse();
        assertThat(rules.isQuerySink("com.acme.dao.UserDao#findById")).isTrue();
        assertThat(rules.isQuerySink("com.acme.dao.UserDao#save")).isFalse();
    }

    @Test
    void ignoresRulesOfOtherTypesAndRegexCharactersInPatterns() {
        var rules = new QuerySinkRules(List.of(
                new LinkRule(LinkRule.Type.PREFIX, "com.acme.*#x", null, LinkRule.Origin.AUTO, null), sink("a.b.C#m(1)")));

        assertThat(rules.rules()).hasSize(1);
        assertThat(rules.isQuerySink("com.acme.Foo#x")).isFalse();
        assertThat(rules.isQuerySink("a.b.C#m(1)")).isTrue();
        assertThat(rules.isQuerySink("a.b.C#m1")).isFalse();
    }
}
