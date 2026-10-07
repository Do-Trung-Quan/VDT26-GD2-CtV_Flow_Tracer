package com.ctv.flowtracer.app.ai.provider;

/** Kết quả thô từ provider (chưa kiểm tra theo schema). */
public record LlmResult(String content, int inputTokens, int outputTokens, String model) {}
