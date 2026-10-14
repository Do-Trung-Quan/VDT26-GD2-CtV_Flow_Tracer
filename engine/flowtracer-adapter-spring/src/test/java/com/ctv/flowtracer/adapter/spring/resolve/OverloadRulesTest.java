package com.ctv.flowtracer.adapter.spring.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Chọn hàm nạp chồng theo kiểu đối số — chỉ loại khi chắc chắn không dùng được. */
class OverloadRulesTest {

    private static final List<List<String>> DELETE_OVERLOADS = List.of(List.of("String"), List.of("Collection<String>"));

    @Test
    void stringArgumentSelectsTheStringOverloadAndCollectionArgumentTheOther() {
        assertThat(OverloadRules.narrow(List.of("delete(String)", "delete(Collection)"), DELETE_OVERLOADS, List.of("String")))
                .containsExactly("delete(String)");
        assertThat(OverloadRules.narrow(List.of("delete(String)", "delete(Collection)"), DELETE_OVERLOADS, List.of("List")))
                .containsExactly("delete(Collection)");
        assertThat(OverloadRules.narrow(List.of("a", "b"), DELETE_OVERLOADS, List.of("Set<String>"))).containsExactly("b");
    }

    @Test
    void unknownOrProjectTypesNeverExcludeAnything() {
        List<String> both = List.of("delete(String)", "delete(Collection)");
        assertThat(OverloadRules.narrow(both, DELETE_OVERLOADS, java.util.Arrays.asList((String) null))).hasSize(2);
        assertThat(OverloadRules.narrow(both, DELETE_OVERLOADS, List.of("OrderEntity"))).hasSize(2);
        assertThat(OverloadRules.narrow(both, DELETE_OVERLOADS, List.of())).hasSize(2);
    }

    @Test
    void numbersAndBoxedTypesAreCompatibleButNotWithStrings() {
        assertThat(OverloadRules.mayAccept("int", "Long")).isTrue();
        assertThat(OverloadRules.mayAccept("Integer", "double")).isTrue();
        assertThat(OverloadRules.mayAccept("int", "String")).isFalse();
        assertThat(OverloadRules.mayAccept("boolean", "String")).isFalse();
        assertThat(OverloadRules.mayAccept("String", "Object")).isTrue();
        assertThat(OverloadRules.mayAccept("String", "T")).isTrue();
    }

    @Test
    void ifEveryCandidateWouldBeExcludedTheOriginalListIsKept() {
        List<List<String>> params = List.of(List.of("Map<String,String>"), List.of("Collection<String>"));
        assertThat(OverloadRules.narrow(List.of("x", "y"), params, List.of("String"))).containsExactly("x", "y");
    }
}
