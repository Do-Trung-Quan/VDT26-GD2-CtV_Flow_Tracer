package com.ctv.flowtracer.adapter.spring.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QueryTextAnalyzerTest {

    private final QueryTextAnalyzer analyzer = new QueryTextAnalyzer();

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            select c from Category c where c.id = :id                                 | SELECT          | READ
            SELECT COUNT(*) FROM product WHERE category_id = ?                         | SELECT COUNT(*) | READ
            select distinct count (p) from Product p                                  | SELECT COUNT(*) | READ
            update Category c set c.deleted = true where c.id = :id                    | UPDATE          | WRITE
            insert into category (code, title) values (:code, :title)                  | INSERT          | WRITE
            delete from Category c where c.id = :id                                    | DELETE          | DELETE
            merge into category using dual on (1 = 1) when matched then update set x=1 | INSERT/UPDATE   | WRITE
            """)
    void recognisesTheStatementKind(String text, String verb, String operation) {
        var analysis = analyzer.analyze(text);

        assertThat(analysis.verb()).isEqualTo(verb.strip());
        assertThat(analysis.operation()).isEqualTo(operation.strip());
    }

    @Test
    void readsEveryTableAfterFromJoinUpdateAndInto() {
        assertThat(analyzer.referencedTables("select c from Category c join Product p on p.cid = c.id left join Brand b on b.id = p.bid"))
                .containsExactly("Category", "Product", "Brand");
        assertThat(analyzer.referencedTables("update Category c set c.x = 1")).containsExactly("Category");
        assertThat(analyzer.referencedTables("insert into category(code) values (:c)")).containsExactly("category");
        assertThat(analyzer.referencedTables("delete from Category where id = 1")).containsExactly("Category");
    }

    @Test
    void handlesCommaSeparatedTablesAliasesAndSchemaPrefix() {
        assertThat(analyzer.referencedTables("select * from a x, b y, c where x.id = y.id")).containsExactly("a", "b", "c");
        assertThat(analyzer.referencedTables("select * from shop.\"Order\" o")).containsExactly("shop.Order");
    }

    @Test
    void ignoresSubqueriesCommentsAndStringLiterals() {
        String text = """
                select c from Category c -- from Secret
                where c.code = 'from Hidden' /* join Other o */
                and c.id in (select p.cid from Product p)
                """;

        assertThat(analyzer.referencedTables(text)).containsExactly("Category", "Product");
    }

    @Test
    void joinedAssociationPathsAreFlaggedSoJpqlCanSkipThem() {
        var analysis = analyzer.analyze("select p from Product p join p.category c join fetch p.brand");

        assertThat(QueryTextAnalyzer.asMap(analysis)).isEqualTo(Map.of("Product", false, "p.category", true, "p.brand", true));
    }

    @Test
    void textWithoutStatementGivesNoVerbAndNoTables() {
        var analysis = analyzer.analyze(" and c.code = :code ");

        assertThat(analysis.verb()).isNull();
        assertThat(analysis.operation()).isNull();
        assertThat(analysis.identifiers()).isEqualTo(List.of());
    }
}
