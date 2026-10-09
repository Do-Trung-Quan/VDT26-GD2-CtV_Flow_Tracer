package com.ctv.flowtracer.app.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.app.workspace.entity.Source;
import com.ctv.flowtracer.app.workspace.entity.Workspace;
import com.ctv.flowtracer.ir.SourceRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NestedSourceRulesTest {

    private final Workspace ws = new Workspace("Shop", null);

    private Source source(String path, SourceRole role) {
        return new Source(ws, path, role, path);
    }

    @Test
    void childInsideParentGetsParentAndParentExcludesChild() {
        Source be = source("shop", SourceRole.BACKEND);
        be.setExcludeGlobs(List.of("generated/**"));
        Source fe = source("shop/frontend", SourceRole.FRONTEND);
        Source legacy = source("shop/docs/old-shop", SourceRole.LEGACY);

        NestedSourceRules.apply(List.of(be, fe, legacy));

        assertThat(fe.getParent()).isSameAs(be);
        assertThat(legacy.getParent()).isSameAs(be);
        assertThat(be.getParent()).isNull();
        assertThat(be.getExcludeGlobs()).containsExactly("generated/**", "frontend/**", "docs/old-shop/**");
    }

    @Test
    void nearestParentWins() {
        Source outer = source("repo", SourceRole.BACKEND);
        Source middle = source("repo/web", SourceRole.FRONTEND);
        Source inner = source("repo/web/legacy", SourceRole.LEGACY);

        NestedSourceRules.apply(List.of(outer, middle, inner));

        assertThat(inner.getParent()).isSameAs(middle);
        assertThat(middle.getExcludeGlobs()).containsExactly("legacy/**");
        assertThat(outer.getExcludeGlobs()).containsExactly("web/**");
    }

    @Test
    void applyIsIdempotentAndSiblingsAreNotNested() {
        Source be = source("shop/backend", SourceRole.BACKEND);
        Source fe = source("shop/backend-web", SourceRole.FRONTEND);

        NestedSourceRules.apply(List.of(be, fe));
        NestedSourceRules.apply(List.of(be, fe));

        assertThat(fe.getParent()).isNull();
        assertThat(be.getExcludeGlobs()).isEmpty();
    }

    @Test
    void detachRemovesOnlyTheChildExclude() {
        Source be = source("shop", SourceRole.BACKEND);
        be.setExcludeGlobs(List.of("generated/**"));
        Source fe = source("shop/frontend", SourceRole.FRONTEND);
        var all = new ArrayList<>(List.of(be, fe));
        NestedSourceRules.apply(all);

        NestedSourceRules.detach(fe, all);

        assertThat(be.getExcludeGlobs()).containsExactly("generated/**");
    }

    @Test
    void requiredExcludesAndDuplicates() {
        Source be = source("shop", SourceRole.BACKEND);
        Source fe = source("shop/frontend", SourceRole.FRONTEND);
        var all = List.of(be, fe);

        assertThat(NestedSourceRules.requiredExcludes(be, all)).containsExactly("frontend/**");
        assertThat(NestedSourceRules.isDuplicate(all, "shop", SourceRole.BACKEND)).isTrue();
        assertThat(NestedSourceRules.isDuplicate(all, "shop", SourceRole.FRONTEND)).isFalse();
    }

    @Test
    void sourceAtSourceRootContainsEverything() {
        Source root = source("", SourceRole.BACKEND);
        Source fe = source("web", SourceRole.FRONTEND);

        NestedSourceRules.apply(List.of(root, fe));

        assertThat(root.getExcludeGlobs()).containsExactly("web/**");
        assertThat(SourceService.defaultName("")).isEqualTo("source-root");
        assertThat(SourceService.defaultName("shop/web")).isEqualTo("web");
    }
}
