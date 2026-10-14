package com.ctv.flowtracer.app.linking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tín hiệu khớp tên giữa chuỗi gọi và từng ứng viên. */
class CandidateHintRuleTest {

    private static final Map<String, String> CANDIDATES = new LinkedHashMap<>();

    static {
        CANDIDATES.put("featured", "GET /api/v1/library/public/featured-books");
        CANDIDATES.put("most", "GET /api/v1/library/public/most-borrowed-books");
        CANDIDATES.put("config", "GET /api/v1/library/public/config");
    }

    @Test
    void wordsSplitCamelCaseAndDropGenericTokens() {
        assertThat(CandidateHintRule.words("HttpHomeRepository.getMostBorrowedBooks")).containsExactly("home", "most", "borrowed", "books");
        assertThat(CandidateHintRule.words("GET /api/v1/x")).isEmpty();
    }

    @Test
    void matchesNamesAcrossPluralAndTenseButOnlyWhereTheyDiscriminate() {
        List<String> context = List.of("loadMostBorrowed", "getMostBorrowedBooks").stream()
                .flatMap(s -> CandidateHintRule.words(s).stream()).toList();

        Map<String, List<String>> hints = CandidateHintRule.hints(context, CANDIDATES);

        assertThat(hints.get("most")).containsExactly("most", "borrowed", "books");
        assertThat(hints.get("featured")).containsExactly("books"); // "books" khớp nhưng ít hơn hẳn
        assertThat(hints.get("config")).isEmpty();
    }

    @Test
    void wordsSharedByEveryCandidateAreNotSignals() {
        List<String> context = CandidateHintRule.words("loadPublicLibrary");

        Map<String, List<String>> hints = CandidateHintRule.hints(context, CANDIDATES);

        assertThat(hints.values()).allMatch(List::isEmpty); // "library" có ở mọi ứng viên
    }

    @Test
    void foldRemovesVietnameseAccents() {
        assertThat(CandidateHintRule.fold("Đường dẫn Khớp")).isEqualTo("duong dan khop");
    }
}
