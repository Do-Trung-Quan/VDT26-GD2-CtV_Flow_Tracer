package com.ctv.flowtracer.app.ai.provider;

import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;

/** provider = none: AI luôn không khả dụng → mọi tính năng đi đường tất định. */
public class NoopLlmProvider implements LlmProvider {

    @Override
    public String providerId() {
        return "none";
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        throw new AppException(ErrorCode.AI_DISABLED, "AI đang tắt (flowtracer.ai.provider=none)");
    }
}
