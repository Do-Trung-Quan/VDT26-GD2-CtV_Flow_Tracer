package com.ctv.flowtracer.core.mock;

import java.util.Map;

/** Bộ sinh dữ liệu mẫu tất định (Datafaker có seed cố định, locale vi) — dùng khi AI tắt hoặc lỗi. */
public final class FallbackMockGenerator {

    /** Seed cố định để cùng schema luôn ra cùng dữ liệu. */
    public static final long SEED = 20260930L;

    /** @return giá trị mẫu thỏa schema */
    public Object generate(Map<String, Object> jsonSchema) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
