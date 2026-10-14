package com.ctv.flowtracer.app.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.app.ai.provider.AiProviderException.Kind;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Phân loại lỗi provider và thời gian nghỉ tương ứng. */
class ProviderErrorClassificationTest {

    @Test
    void quotaErrorsAreRecognisedWithRetryDelayAndDailyFlag() {
        var e = SpringAiLlmProvider.classify(new RuntimeException("429 RESOURCE_EXHAUSTED. Please retry in 31.5s. quota exceeded PerDay"));

        assertThat(e.kind()).isEqualTo(Kind.QUOTA);
        assertThat(e.retryAfter()).isEqualTo(Duration.ofMillis(31500));
        assertThat(e.daily()).isTrue();
    }

    @Test
    void overloadAuthTimeoutAndBadRequestAreToldApart() {
        assertThat(SpringAiLlmProvider.classify(new RuntimeException("503 UNAVAILABLE: model is overloaded")).kind()).isEqualTo(Kind.OVERLOADED);
        assertThat(SpringAiLlmProvider.classify(new RuntimeException("403 PERMISSION_DENIED")).kind()).isEqualTo(Kind.AUTH);
        assertThat(SpringAiLlmProvider.classify(new RuntimeException("Read timed out")).kind()).isEqualTo(Kind.TIMEOUT);
        assertThat(SpringAiLlmProvider.classify(new RuntimeException("400 INVALID_ARGUMENT")).kind()).isEqualTo(Kind.BAD_REQUEST);
        assertThat(SpringAiLlmProvider.classify(new RuntimeException("boom")).kind()).isEqualTo(Kind.OTHER);
    }

    @Test
    void anErrorCodeInsideALongerNumberIsNotAStatus() {
        assertThat(SpringAiLlmProvider.classify(new RuntimeException("request id 14290 failed")).kind()).isEqualTo(Kind.OTHER);
    }

    @Test
    void secretsInProviderMessagesAreMasked() {
        var e = SpringAiLlmProvider.classify(new RuntimeException("403 bad request ?key=AIzaSyA1234567890123456789012345678901 password=hunter2"));

        assertThat(e.getMessage()).doesNotContain("hunter2").doesNotContain("AIzaSyA1234567890123456789012345678901");
    }

    @Test
    void geminiDailyQuotaBodyGivesTheRealResetTimeAndThePlanLimit() {
        var e = SpringAiLlmProvider.classify(new RuntimeException(
                "429 You exceeded your current quota. * Quota exceeded for metric: generate_content_free_tier_requests, limit: 20, model: m "
                        + "Please retry in 18h26m53.007940418s. quotaId: GenerateRequestsPerDayPerProjectPerModel-FreeTier"));

        assertThat(e.kind()).isEqualTo(Kind.QUOTA);
        assertThat(e.daily()).isTrue();
        assertThat(e.quotaLimit()).isEqualTo(20);
        assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(18 * 3600 + 26 * 60 + 53).plusMillis(8));
    }

    @Test
    void retryDelayInSecondsAndMinutesAreBothUnderstood() {
        assertThat(SpringAiLlmProvider.classify(new RuntimeException("429 \"retryDelay\": \"66413s\"")).retryAfter())
                .isEqualTo(Duration.ofSeconds(66413));
        assertThat(SpringAiLlmProvider.classify(new RuntimeException("429 quota. retry in 2m5s")).retryAfter()).isEqualTo(Duration.ofSeconds(125));
    }

    @Test
    void sdkTimeoutWrappedAroundASocketClosedIsStillATimeout() {
        // SDK Gemini khi hết giờ ném GenAiIOException → InterruptedIOException("timeout") → SocketException("Socket closed")
        var closed = new java.net.SocketException("Socket closed");
        var timeout = new java.io.InterruptedIOException("timeout");
        timeout.initCause(closed);
        var e = SpringAiLlmProvider.classify(new RuntimeException("Failed to execute HTTP request.", timeout));

        assertThat(e.kind()).isEqualTo(Kind.TIMEOUT);
        assertThat(SpringAiLlmProvider.classify(new java.net.ConnectException("Connection refused")).kind()).isEqualTo(Kind.OTHER);
    }
}
