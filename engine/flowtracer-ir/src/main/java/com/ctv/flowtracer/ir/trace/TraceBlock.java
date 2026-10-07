package com.ctv.flowtracer.ir.trace;

import java.util.Objects;

/** Khối điều kiện/lặp bao một dải bước trên sequence diagram. */
public record TraceBlock(Type type, String label, int fromStep, int toStep) {

    public enum Type {
        ALT,
        OPT,
        LOOP
    }

    public TraceBlock {
        Objects.requireNonNull(type, "type");
        if (fromStep > toStep) {
            throw new IllegalArgumentException("fromStep > toStep");
        }
    }
}
