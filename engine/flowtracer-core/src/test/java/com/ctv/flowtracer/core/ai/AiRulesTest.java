package com.ctv.flowtracer.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctv.flowtracer.core.explain.ElementNameRule;
import com.ctv.flowtracer.core.scenario.ScenarioPolishRule;
import com.ctv.flowtracer.ir.scenario.Scenario;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiRulesTest {

    private final Scenario framework = new Scenario(
            List.of(new Scenario.Actor("user", "User", "Người dùng"), new Scenario.Actor("fe", "Frontend", "Giao diện")),
            List.of(
                    new Scenario.Item(1, "User gửi biểu mẫu bằng \"Lưu\" trên Frontend.", 0, false),
                    new Scenario.Item(null, "Nếu x:", 0, false),
                    new Scenario.Item(2, "Frontend trả lỗi cho User.", 1, false)));

    @Test
    void polishKeepsStepNumbersOrderAndComponents() {
        var polished = List.of(
                Map.entry(1, "Người dùng (User) bấm Lưu trên Frontend để gửi biểu mẫu."),
                Map.entry(2, "Frontend báo lỗi cho User."));

        assertThat(new ScenarioPolishRule().apply(framework, polished)).containsOnlyKeys(1, 2);
    }

    @Test
    void polishIsRejectedWhenAStepIsMissingReorderedOrDropsAComponent() {
        var rule = new ScenarioPolishRule();

        assertThatThrownBy(() -> rule.apply(framework, List.of(Map.entry(1, "User gửi trên Frontend."))))
                .isInstanceOf(ScenarioPolishRule.Rejected.class).hasMessageContaining("Số bước lệch");
        assertThatThrownBy(() -> rule.apply(framework, List.of(Map.entry(2, "Frontend báo lỗi cho User."), Map.entry(1, "User gửi trên Frontend."))))
                .isInstanceOf(ScenarioPolishRule.Rejected.class).hasMessageContaining("Thứ tự");
        assertThatThrownBy(() -> rule.apply(framework, List.of(Map.entry(1, "Người dùng gửi biểu mẫu trên Frontend."), Map.entry(2, "Frontend báo lỗi cho User."))))
                .isInstanceOf(ScenarioPolishRule.Rejected.class).hasMessageContaining("User");
    }

    @Test
    void elementNamesMustBeReadableShortAndWithoutTypePrefix() {
        var rule = new ElementNameRule();

        assertThat(rule.clean("Nút Xuất danh sách ra Excel")).isEqualTo("Xuất danh sách ra Excel");
        assertThat(rule.clean("  \"xóa tác giả\" ")).isEqualTo("Xóa tác giả");
        assertThat(rule.clean("Ô Họ và tên")).isEqualTo("Họ và tên");
        assertThat(rule.clean("onSelectionChange")).isNull();
        assertThat(rule.clean("export_excel")).isNull();
        assertThat(rule.clean("this.save()")).isNull();
        assertThat(rule.clean("x")).isNull();
        assertThat(rule.clean("a".repeat(80))).isNull();
        assertThat(rule.clean(null)).isNull();
    }
}
