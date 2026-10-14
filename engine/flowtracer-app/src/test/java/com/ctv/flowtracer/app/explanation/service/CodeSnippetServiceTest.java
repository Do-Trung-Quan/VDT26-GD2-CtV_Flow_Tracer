package com.ctv.flowtracer.app.explanation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tự tìm điểm kết thúc của phần tử HTML / câu lệnh gọi khi dữ liệu quét chỉ có dòng đầu (Xem code không bị cắt cụt). */
class CodeSnippetServiceTest {

    @Test
    void htmlElementEndsAtItsMatchingClosingTag() {
        List<String> lines = List.of(
                "<shared-table",
                "  [rows]=\"authors()\"",
                "  (rowClick)=\"open($event)\">",
                "  <shared-column field=\"name\"></shared-column>",
                "  <shared-table-footer></shared-table-footer>",
                "</shared-table>",
                "<p>sau</p>");

        assertThat(CodeSnippetService.htmlElementEnd(lines, 55)).isEqualTo(60);
    }

    @Test
    void spanReportsWhereTheOpeningTagEndsAndWhereTheElementEnds() {
        List<String> lines = List.of("<shared-table", "  [rows]=\"r\"", "  (x)=\"y\">", "  <ng-template></ng-template>", "</shared-table>", "<p></p>");

        assertThat(CodeSnippetService.htmlElementSpan(lines, 10)).containsExactly(12, 14);
    }

    @Test
    void nestedSameTagIsCounted() {
        List<String> lines = List.of("<div class=\"a\">", "  <div>x</div>", "</div>", "<div>khác</div>");

        assertThat(CodeSnippetService.htmlElementEnd(lines, 10)).isEqualTo(12);
    }

    @Test
    void greaterThanInsideAnAttributeDoesNotEndTheTag() {
        List<String> lines = List.of("<button", "  [disabled]=\"count() > 0\"", "  (click)=\"save()\">Lưu</button>", "<x></x>");

        assertThat(CodeSnippetService.htmlElementEnd(lines, 3)).isEqualTo(5);
    }

    @Test
    void selfClosingAndVoidElementsEndWithTheirOpeningTag() {
        assertThat(CodeSnippetService.htmlElementEnd(List.of("<app-icon", "  name=\"x\" />", "<b></b>"), 7)).isEqualTo(8);
        assertThat(CodeSnippetService.htmlElementEnd(List.of("<input", "  type=\"text\">", "<b></b>"), 7)).isEqualTo(8);
    }

    @Test
    void unbalancedElementFallsBackToItsOpeningTag() {
        assertThat(CodeSnippetService.htmlElementEnd(List.of("<div>", "chưa đóng", "<p>x</p>"), 20)).isEqualTo(20);
    }

    @Test
    void callStatementEndsWhereTheChainEnds() {
        List<String> lines = List.of(
                "this.http",
                "  .get(`${this.base}/authors`, { params })",
                "  .pipe(map((r) => r.data), catchError(() => of([])));",
                "const next = 1;");

        assertThat(CodeSnippetService.statementEnd(lines, 30)).isEqualTo(32);
    }

    @Test
    void singleLineCallStaysOnItsLine() {
        assertThat(CodeSnippetService.statementEnd(List.of("this.http.post(url, body);", "other();"), 5)).isEqualTo(5);
    }
}
