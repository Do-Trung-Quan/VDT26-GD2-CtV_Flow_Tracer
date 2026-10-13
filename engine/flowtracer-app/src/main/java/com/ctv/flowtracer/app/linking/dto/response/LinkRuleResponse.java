package com.ctv.flowtracer.app.linking.dto.response;

import com.ctv.flowtracer.ir.LinkRule;

/**
 * Một luật liên kết.
 *
 * @param type loại luật
 * @param pattern mẫu cần khớp
 * @param replacement giá trị thay thế (có thể null)
 * @param origin {@code AUTO} (tự phát hiện) / {@code MANUAL} (thủ công)
 * @param description mô tả, vd nơi phát hiện
 */
public record LinkRuleResponse(
        LinkRule.Type type,
        String pattern,
        String replacement,
        LinkRule.Origin origin,
        String description) {

    public static LinkRuleResponse from(LinkRule rule) {
        return new LinkRuleResponse(rule.type(), rule.pattern(), rule.replacement(), rule.origin(), rule.description());
    }
}
