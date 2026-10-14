package com.ctv.flowtracer.app.linking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.app.linking.service.AdviceConsistencyRule.Item;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Lời giải thích của AI phải có bằng chứng thật và không "Nên chọn" nhiều đích cho cùng một lời gọi. */
class AdviceConsistencyRuleTest {

    private static final String CONTEXT = "1. Hàm HomeRepo.getMostBorrowedBooks — dòng mã: return this.get('most-borrowed-books', ref)";

    @Test
    void shouldWithoutRealEvidenceBecomesUnsure() {
        var out = AdviceConsistencyRule.apply(
                List.of(new Item("a", "SHOULD", "phù hợp trang chủ", ""), new Item("b", "SHOULD", "khớp", "chuỗi bịa đặt")), Map.of(), CONTEXT);

        assertThat(out).extracting(Item::advice).containsExactly("UNSURE", "UNSURE");
        assertThat(out).extracting(Item::evidence).containsOnlyNulls();
    }

    @Test
    void shouldWithEvidenceFoundInTheContextIsKeptEvenWithDifferentCase() {
        var out = AdviceConsistencyRule.apply(List.of(new Item("a", "SHOULD", "khớp chuỗi", "MOST-borrowed-books")), Map.of(), CONTEXT);

        assertThat(out.get(0).advice()).isEqualTo("SHOULD");
        assertThat(out.get(0).evidence()).isEqualTo("MOST-borrowed-books");
    }

    @Test
    void severalShouldKeepOnlyTheOneWithTheMostNameHints() {
        var items = List.of(new Item("a", "SHOULD", "r", "most-borrowed-books"), new Item("b", "SHOULD", "r", "getMostBorrowedBooks"),
                new Item("c", "SHOULD_NOT", "r", null));

        var out = AdviceConsistencyRule.apply(items, Map.of("a", 3, "b", 1), CONTEXT);

        assertThat(out).extracting(Item::advice).containsExactly("SHOULD", "UNSURE", "SHOULD_NOT");
    }

    @Test
    void severalShouldWithATieOnHintsAreAllDemoted() {
        var items = List.of(new Item("a", "SHOULD", "r", "most-borrowed-books"), new Item("b", "SHOULD", "r", "getMostBorrowedBooks"));

        assertThat(AdviceConsistencyRule.apply(items, Map.of("a", 2, "b", 2), CONTEXT)).extracting(Item::advice).containsExactly("UNSURE", "UNSURE");
        assertThat(AdviceConsistencyRule.apply(items, Map.of(), CONTEXT)).extracting(Item::advice).containsExactly("UNSURE", "UNSURE");
    }
}
