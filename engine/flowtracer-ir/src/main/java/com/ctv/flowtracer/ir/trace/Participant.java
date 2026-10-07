package com.ctv.flowtracer.ir.trace;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/** Một cột (lifeline) trên sequence diagram. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Participant(String id, String label, Layer layer, String nodeKey) {

    public enum Layer {
        ACTOR,
        FE,
        BE,
        DATA,
        EXTERNAL
    }

    public Participant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(layer, "layer");
    }
}
