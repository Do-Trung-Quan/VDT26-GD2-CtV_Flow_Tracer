package com.ctv.flowtracer.app.tracing.dto.response;

import com.ctv.flowtracer.ir.trace.Participant;

/**
 * Một cột (lifeline) của sequence diagram.
 *
 * @param id id cột, được các bước dùng để chỉ nguồn/đích
 * @param label nhãn hiển thị, vd "Service Danh mục"
 * @param role vai trò của cột (Client, Controller, Service, Repository, Database…)
 * @param nodeKey node đại diện cho cột (method đầu tiên đi qua lớp); null với Client và Database
 */
public record TraceParticipantResponse(String id, String label, Participant.Role role, String nodeKey) {

    public static TraceParticipantResponse from(Participant p) {
        return new TraceParticipantResponse(p.id(), p.label(), p.role(), p.nodeKey());
    }
}
