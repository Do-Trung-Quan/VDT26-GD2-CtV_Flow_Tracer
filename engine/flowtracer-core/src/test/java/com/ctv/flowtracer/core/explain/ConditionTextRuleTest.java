package com.ctv.flowtracer.core.explain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConditionTextRuleTest {

    private final ConditionTextRule rule = new ConditionTextRule();

    @Test
    void rewritesNullAndComparisonsAndKeepsGrouping() {
        assertThat(rule.describe("status == null || (status != 0 && status != 1)"))
                .isEqualTo("status để trống hoặc (status khác 0 và status khác 1)");
    }

    @Test
    void rewritesLengthAndEmptyChecks() {
        assertThat(rule.describe("title.length() > DeclSupport.TITLE_MAX_LENGTH")).isEqualTo("title dài quá giới hạn cho phép");
        assertThat(rule.describe("pseudonym.length() > DeclSupport…")).isEqualTo("pseudonym dài quá giới hạn cho phép");
        assertThat(rule.describe("name.length() > 50")).isEqualTo("name dài quá 50 ký tự");
        assertThat(rule.describe("ids.isEmpty()")).isEqualTo("ids để trống");
        assertThat(rule.describe("!ids.isEmpty()")).isEqualTo("ids có nội dung");
        assertThat(rule.describe("count > 0")).isEqualTo("count lớn hơn 0");
    }

    @Test
    void dropsThisPrefixButKeepsUnknownAtomsVerbatim() {
        assertThat(rule.describe("this.isEdit && this.data.item")).isEqualTo("this.isEdit && this.data.item");
        assertThat(rule.describe("repo.existsByCode(code) || code == null")).isEqualTo("repo.existsByCode(code) hoặc code để trống");
    }

    @Test
    void returnsConditionUnchangedWhenNothingIsRecognised() {
        assertThat(rule.describe("categoryRepository.existsByCode(code)")).isEqualTo("categoryRepository.existsByCode(code)");
        assertThat(rule.describe(null)).isNull();
    }
}
