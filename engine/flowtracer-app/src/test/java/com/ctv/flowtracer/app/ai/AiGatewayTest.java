package com.ctv.flowtracer.app.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ctv.flowtracer.app.ai.entity.AiArtifact;
import com.ctv.flowtracer.app.ai.entity.LlmCallLog;
import com.ctv.flowtracer.app.ai.entity.LlmCallStatus;
import com.ctv.flowtracer.app.ai.model.AiCall;
import com.ctv.flowtracer.app.ai.model.AiOutcome;
import com.ctv.flowtracer.app.ai.model.AiPurpose;
import com.ctv.flowtracer.app.ai.model.AiRejectedException;
import com.ctv.flowtracer.app.ai.provider.AiProviderException;
import com.ctv.flowtracer.app.ai.provider.LlmProvider;
import com.ctv.flowtracer.app.ai.provider.LlmRequest;
import com.ctv.flowtracer.app.ai.provider.LlmResult;
import com.ctv.flowtracer.app.ai.repository.AiArtifactRepository;
import com.ctv.flowtracer.app.ai.repository.LlmCallLogRepository;
import com.ctv.flowtracer.app.ai.service.AiCallTrace;
import com.ctv.flowtracer.app.ai.service.AiGateway;
import com.ctv.flowtracer.app.ai.service.AiSettingsService;
import com.ctv.flowtracer.app.ai.service.ProviderHealth;
import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.config.FlowTracerProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Cổng AI với provider giả: cache, che secret, hạn mức, kiểm tra đầu ra, tắt mục đích. */
class AiGatewayTest {

    private final UUID ws = UUID.randomUUID();
    private final List<AiArtifact> stored = new ArrayList<>();
    private final List<LlmCallLog> logged = new ArrayList<>();
    private final List<LlmRequest> sent = new ArrayList<>();
    private String reply = "{\"purpose\": \"Mô tả ngắn\"}";
    private boolean providerAvailable = true;
    private boolean purposeEnabled = true;
    private long tokensToday = 0;
    private RuntimeException failWith;
    private CountDownLatch gate;
    private final CountDownLatch release = new CountDownLatch(1);
    private final ProviderHealth health = new ProviderHealth();
    private AiGateway gateway;

    @BeforeEach
    void setUp() {
        LlmProvider provider = new LlmProvider() {
            @Override
            public String providerId() {
                return "fake";
            }

            @Override
            public boolean available() {
                return providerAvailable;
            }

            @Override
            public LlmResult complete(LlmRequest request) {
                sent.add(request);
                if (failWith != null) {
                    throw failWith;
                }
                if (gate != null) {
                    gate.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return new LlmResult(reply, 100, 50, "fake-model");
            }
        };
        AiSettingsService settings = mock(AiSettingsService.class);
        when(settings.isEnabled(any())).thenAnswer(i -> purposeEnabled);
        when(settings.tokensToday()).thenAnswer(i -> tokensToday);
        AiArtifactRepository artifacts = mock(AiArtifactRepository.class);
        when(artifacts.findByWorkspaceIdAndPurposeAndTargetKeyAndInputHash(any(), any(), any(), any())).thenAnswer(i -> stored.stream()
                .filter(a -> a.getInputHash().equals(i.getArgument(3)) && a.getTargetKey().equals(i.getArgument(2)))
                .findFirst());
        when(artifacts.save(any(AiArtifact.class))).thenAnswer(i -> {
            AiArtifact a = i.getArgument(0);
            if (!stored.contains(a)) {
                stored.add(a);
            }
            return a;
        });
        LlmCallLogRepository logs = mock(LlmCallLogRepository.class);
        when(logs.save(any(LlmCallLog.class))).thenAnswer(i -> {
            logged.add(i.getArgument(0));
            return i.getArgument(0);
        });
        FlowTracerProperties properties = mock(FlowTracerProperties.class);
        when(properties.ai()).thenReturn(new FlowTracerProperties.Ai("fake", "fake-model", "", 1000));
        gateway = new AiGateway(provider, settings, artifacts, logs, properties, JsonMapper.builder().build(), health, new AiCallTrace(false));
    }

    private AiCall call(String user) {
        return new AiCall(ws, AiPurpose.PURPOSE_TEXT, "node-1", "purpose-text.v1", "system", user, "{...}", 500, raw -> {
            if (!(raw.get("purpose") instanceof String s) || s.isBlank()) {
                throw new AiRejectedException("thiếu purpose");
            }
            return Map.of("purpose", s);
        });
    }

    @Test
    void secondCallWithSameInputHitsTheCacheAndDoesNotCallTheProvider() {
        AiOutcome first = gateway.execute(call("code A"), false);
        AiOutcome second = gateway.execute(call("code A"), false);

        assertThat(first.status()).isEqualTo(AiOutcome.Status.GENERATED);
        assertThat(second.status()).isEqualTo(AiOutcome.Status.CACHED);
        assertThat(second.content()).containsEntry("purpose", "Mô tả ngắn");
        assertThat(sent).hasSize(1);
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().getHitCount()).isEqualTo(1);
    }

    @Test
    void changedInputOrForceCallsTheProviderAgain() {
        gateway.execute(call("code A"), false);
        gateway.execute(call("code B"), false);
        gateway.execute(call("code B"), true);

        assertThat(sent).hasSize(3);
    }

    @Test
    void secretsAreMaskedBeforeSendingAndPromptsAreNeverLogged() {
        gateway.execute(call("spring.datasource.password=Sup3rS3cret\nString keyword = x;"), false);

        assertThat(sent.getFirst().user()).doesNotContain("Sup3rS3cret").contains("password=***").contains("keyword = x");
        assertThat(logged).singleElement().satisfies(l -> {
            assertThat(l.getStatus()).isEqualTo(LlmCallStatus.OK);
            assertThat(l.getPromptHash()).hasSize(64);
            assertThat(l.getInputTokens()).isEqualTo(100);
        });
    }

    @Test
    void overTheDailyQuotaTheProviderIsNotCalled() {
        tokensToday = 1000;

        AiOutcome outcome = gateway.execute(call("code A"), false);

        assertThat(outcome.status()).isEqualTo(AiOutcome.Status.QUOTA_EXCEEDED);
        assertThat(outcome.hasContent()).isFalse();
        assertThat(sent).isEmpty();
        assertThat(logged).singleElement().extracting(LlmCallLog::getStatus).isEqualTo(LlmCallStatus.QUOTA_EXCEEDED);
    }

    @Test
    void invalidOutputIsRejectedAndNotStored() {
        reply = "{\"khac\": 1}";

        AiOutcome outcome = gateway.execute(call("code A"), false);

        assertThat(outcome.status()).isEqualTo(AiOutcome.Status.REJECTED);
        assertThat(stored).isEmpty();
        assertThat(logged).singleElement().extracting(LlmCallLog::getStatus).isEqualTo(LlmCallStatus.REJECTED_BY_VALIDATION);
    }

    @Test
    void replyWrappedInACodeFenceIsStillParsed() {
        reply = "```json\n{\"purpose\": \"Có rào\"}\n```";

        assertThat(gateway.execute(call("code A"), false).content()).containsEntry("purpose", "Có rào");
    }

    @Test
    void textWithoutJsonIsRejected() {
        reply = "Xin lỗi, tôi không thể.";

        assertThat(gateway.execute(call("code A"), false).status()).isEqualTo(AiOutcome.Status.REJECTED);
    }

    @Test
    void disabledPurposeOrProviderGivesNoContentAndNoCall() {
        purposeEnabled = false;
        assertThat(gateway.execute(call("code A"), false).status()).isEqualTo(AiOutcome.Status.DISABLED);
        assertThat(gateway.peek(call("code A"))).isEmpty();

        purposeEnabled = true;
        providerAvailable = false;
        assertThat(gateway.execute(call("code A"), false).status()).isEqualTo(AiOutcome.Status.DISABLED);
        assertThat(sent).isEmpty();
    }

    @Test
    void quotaErrorStartsACooldownAndTheProviderIsNotCalledAgain() {
        failWith = new AiProviderException(AiProviderException.Kind.QUOTA, "429", Duration.ofSeconds(30), false);

        AiOutcome first = gateway.execute(call("code A"), false);
        AiOutcome second = gateway.execute(call("code B"), false);

        assertThat(first.status()).isEqualTo(AiOutcome.Status.QUOTA_EXCEEDED);
        assertThat(first.message()).contains("429").contains("nội dung tự sinh");
        assertThat(second.status()).isEqualTo(AiOutcome.Status.QUOTA_EXCEEDED);
        assertThat(sent).hasSize(1);
        assertThat(logged).extracting(LlmCallLog::getStatus).containsExactly(LlmCallStatus.QUOTA_EXCEEDED);
        AppException error = gateway.failure(second);
        assertThat(error.code()).isEqualTo(ErrorCode.AI_QUOTA_EXCEEDED);
        assertThat(error.details()).containsKeys("retryAt", "reason");
    }

    @Test
    void overloadIsReportedButDoesNotBlockTheNextCall() {
        failWith = new AiProviderException(AiProviderException.Kind.OVERLOADED, "503", null, false);
        assertThat(gateway.execute(call("code A"), false).status()).isEqualTo(AiOutcome.Status.UNAVAILABLE);
        // 503 chỉ để HIỂN THỊ ("AI quá tải"); không chặn gọi — người dùng bấm lại là thử lại thật
        assertThat(health.snapshot().failing()).isTrue();
        assertThat(health.snapshot().cooling()).isFalse();

        failWith = null;
        assertThat(gateway.execute(call("code A"), false).status()).isEqualTo(AiOutcome.Status.GENERATED);
        assertThat(health.snapshot().failing()).isFalse();
        assertThat(sent).hasSize(2);
    }

    @Test
    void otherErrorsAndTimeoutsDoNotBlockEitherAndKeepTheirOwnMessage() {
        failWith = new AiProviderException(AiProviderException.Kind.TIMEOUT, "timeout", null, false);
        AiOutcome first = gateway.execute(call("code A"), false);
        AiOutcome second = gateway.execute(call("code B"), false);

        assertThat(first.message()).contains("quá lâu").doesNotContain("thử lại sau");
        assertThat(second.status()).isEqualTo(AiOutcome.Status.UNAVAILABLE);
        assertThat(sent).hasSize(2); // cả hai đều thật sự gọi provider
        assertThat(gateway.failure(second).details()).containsOnlyKeys("reason");
    }

    @Test
    void cachedContentIsServedDuringACooldown() {
        gateway.execute(call("code A"), false);
        health.failure(new AiProviderException(AiProviderException.Kind.QUOTA, "429", null, true));

        assertThat(gateway.execute(call("code A"), false).status()).isEqualTo(AiOutcome.Status.CACHED);
        assertThat(gateway.offReason(AiPurpose.PURPOSE_TEXT)).contains("429");
    }

    @Test
    void identicalConcurrentRequestsShareOneProviderCall() throws Exception {
        gate = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<AiOutcome> a = pool.submit(() -> gateway.execute(call("code A"), false));
            assertThat(gate.await(5, TimeUnit.SECONDS)).isTrue();
            Future<AiOutcome> b = pool.submit(() -> gateway.execute(call("code A"), false));
            Thread.sleep(200);
            release.countDown();

            assertThat(a.get(5, TimeUnit.SECONDS).hasContent()).isTrue();
            assertThat(b.get(5, TimeUnit.SECONDS).hasContent()).isTrue();
            assertThat(sent).hasSize(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void cachedContentIsStillServedWhenTheProviderIsOff() {
        gateway.execute(call("code A"), false);
        providerAvailable = false;

        assertThat(gateway.execute(call("code A"), false).status()).isEqualTo(AiOutcome.Status.CACHED);
        assertThat(gateway.peek(call("code A"))).isPresent();
        assertThat(gateway.peek(call("code Z"))).isEqualTo(Optional.empty());
        assertThat(gateway.peek(call("code A")).orElseThrow().content()).containsEntry("purpose", "Mô tả ngắn");
    }
}
