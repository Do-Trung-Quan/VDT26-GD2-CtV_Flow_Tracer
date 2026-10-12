package com.ctv.flowtracer.ir.trace;

import java.util.List;
import java.util.Objects;

/**
 * Khối điều kiện/lặp bao một dải bước trên sequence diagram. Khối có thể lồng nhau.
 *
 * <p>Nhánh chính là {@code [fromStep .. nhánh else đầu tiên - 1]} với nhãn {@code label}; mỗi nhánh {@code else} kéo dài
 * tới trước nhánh kế tiếp, nhánh cuối kéo dài tới {@code toStep}. {@code OPT} và {@code LOOP} không có nhánh else.
 *
 * @param label điều kiện của nhánh chính (vd {@code categoryRepository.existsByCode(code)})
 * @param fromStep số thứ tự bước đầu tiên trong khối (từ 1)
 * @param toStep số thứ tự bước cuối cùng trong khối
 * @param elseBranches các nhánh {@code else}, theo thứ tự
 */
public record TraceBlock(Type type, String label, int fromStep, int toStep, List<Branch> elseBranches) {

    public enum Type {
        ALT,
        OPT,
        LOOP
    }

    /** Một nhánh {@code else}: nhãn và bước đầu tiên của nhánh. */
    public record Branch(String label, int fromStep) {

        public Branch {
            Objects.requireNonNull(label, "label");
        }
    }

    public TraceBlock {
        Objects.requireNonNull(type, "type");
        if (fromStep > toStep) {
            throw new IllegalArgumentException("fromStep > toStep");
        }
        elseBranches = elseBranches == null ? List.of() : List.copyOf(elseBranches);
    }

    /** Khối không có nhánh else. */
    public TraceBlock(Type type, String label, int fromStep, int toStep) {
        this(type, label, fromStep, toStep, List.of());
    }
}
