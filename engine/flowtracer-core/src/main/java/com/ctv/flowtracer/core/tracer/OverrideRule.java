package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.ir.EdgeKind;

/** Liên kết chốt tay của người dùng, đã đọc từ CSDL và truyền vào lõi. */
public record OverrideRule(String fromKey, String toKey, EdgeKind edgeKind, Decision decision) {

    public enum Decision {
        /** Ghim đích này, bỏ mọi đích tự động khác. */
        PIN,
        /** Liên kết này sai, bỏ cạnh. */
        REJECT
    }
}
