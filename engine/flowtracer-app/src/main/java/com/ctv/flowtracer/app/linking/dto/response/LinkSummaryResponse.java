package com.ctv.flowtracer.app.linking.dto.response;

import com.ctv.flowtracer.app.linking.model.LinkSummary;

/**
 * Kết quả nối lại sau khi sửa luật.
 *
 * @param calls số lời gọi HTTP trong Workspace
 * @param certain số liên kết chắc chắn
 * @param inferred số liên kết suy luận
 * @param unresolved số lời gọi chưa xác định được đích
 * @param external số lời gọi tới hệ thống ngoài
 * @param changed số lời gọi có đích hoặc mức tin cậy thay đổi so với trước khi sửa luật
 */
public record LinkSummaryResponse(
        int calls,
        int certain,
        int inferred,
        int unresolved,
        int external,
        int changed) {

    public static LinkSummaryResponse from(LinkSummary s) {
        return new LinkSummaryResponse(s.calls(), s.certain(), s.inferred(), s.unresolved(), s.external(), s.changed());
    }
}
