package com.ctv.flowtracer.ir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IrModelTest {

    @Test
    void nodeAttrsAreSortedForDeterminism() {
        var node = new IrNode("java:class:a.B", NodeKind.BE_METHOD, "B", null, null, Map.of("z", 1, "a", 2));
        assertThat(node.attrs().keySet()).containsExactly("a", "z");
    }

    @Test
    void edgeRequiresLocationOrReason() {
        assertThatThrownBy(() -> new IrEdge("a", "b", EdgeKind.CALLS, Confidence.CERTAIN, null, 1, null, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void edgeWithoutTargetMustBeUnresolvedOrHaveCandidates() {
        assertThatThrownBy(() -> new IrEdge("a", null, EdgeKind.CALLS, Confidence.INFERRED, "x", 1, null, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        var withCandidates = new IrEdge(
                "a", null, EdgeKind.CALLS, Confidence.INFERRED, "2 impl", 1, null, List.of(new Candidate("b", 0.5, null)), null);
        assertThat(withCandidates.candidates()).hasSize(1);
    }

    @Test
    void nodeKeysRoundTrip() {
        String key = NodeKeys.javaMethod("com.acme.CategoryService", "create", List.of("CategoryReq"));
        assertThat(key).isEqualTo("java:method:com.acme.CategoryService#create(CategoryReq)");
        var parts = NodeKeys.parse(key);
        assertThat(parts.namespace()).isEqualTo("java");
        assertThat(parts.type()).isEqualTo("method");
        assertThat(NodeKeys.javaEndpoint("post", "/api/v1/categories")).isEqualTo("java:endpoint:POST /api/v1/categories");
    }
}
