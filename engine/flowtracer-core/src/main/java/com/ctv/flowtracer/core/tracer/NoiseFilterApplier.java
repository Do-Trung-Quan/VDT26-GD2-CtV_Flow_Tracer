package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NoiseFilter;
import java.util.List;

/** Bỏ node khớp bộ lọc nhiễu, nối thẳng bước trước với bước sau. */
public final class NoiseFilterApplier {

    public boolean isNoise(IrNode node, List<NoiseFilter> filters) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
