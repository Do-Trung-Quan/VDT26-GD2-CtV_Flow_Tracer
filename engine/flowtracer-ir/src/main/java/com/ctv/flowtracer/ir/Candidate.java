package com.ctv.flowtracer.ir;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/** Một ứng viên đích của edge khi phân tích tĩnh tìm ra nhiều khả năng. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Candidate(String nodeKey, double score, String reason) {

    public Candidate {
        Objects.requireNonNull(nodeKey, "nodeKey");
    }
}
