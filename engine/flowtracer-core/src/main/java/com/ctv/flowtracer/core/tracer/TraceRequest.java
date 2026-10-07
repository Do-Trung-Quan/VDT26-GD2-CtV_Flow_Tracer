package com.ctv.flowtracer.core.tracer;

import com.ctv.flowtracer.ir.NoiseFilter;
import com.ctv.flowtracer.ir.trace.DetailLevel;
import java.util.List;

/** Yêu cầu dò vết. */
public record TraceRequest(
        String startKey,
        DetailLevel detailLevel,
        int maxDepth,
        String scanRunId,
        List<NoiseFilter> noiseFilters,
        List<OverrideRule> overrides) {

    public static final int DEFAULT_MAX_DEPTH = 12;

    public TraceRequest {
        detailLevel = detailLevel == null ? DetailLevel.COMPONENT : detailLevel;
        maxDepth = maxDepth <= 0 ? DEFAULT_MAX_DEPTH : maxDepth;
        noiseFilters = noiseFilters == null ? List.of() : List.copyOf(noiseFilters);
        overrides = overrides == null ? List.of() : List.copyOf(overrides);
    }
}
