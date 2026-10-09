package com.ctv.flowtracer.ir.trace;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * Một cột (lifeline) trên sequence diagram. Quy tắc dựng cột: §2.2.4 của overview.
 *
 * @param id id cột, dùng trong {@link TraceStep#from()} / {@link TraceStep#to()}
 * @param label nhãn hiển thị, vd "Service Danh mục"
 * @param role vai trò của cột
 * @param nodeKey node đại diện cho cột (null với User, Frontend, Database)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Participant(String id, String label, Role role, String nodeKey) {

    /** Vai trò cột. Frontend và Database luôn gộp thành một cột; mỗi Controller/Service/Repository là một cột riêng. */
    public enum Role {
        /** Người dùng (luồng bắt đầu từ FE) hoặc nơi gọi API (luồng bắt đầu từ BE). */
        USER,
        FRONTEND,
        CONTROLLER,
        SERVICE,
        REPOSITORY,
        DATABASE,
        /** Service khác (microservice) hoặc hệ thống bên ngoài Workspace. */
        EXTERNAL
    }

    public Participant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(role, "role");
    }
}
