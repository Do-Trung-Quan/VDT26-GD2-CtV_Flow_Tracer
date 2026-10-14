package com.ctv.flowtracer.app.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.app.ai.provider.AiProviderException;
import com.ctv.flowtracer.app.ai.provider.AiProviderException.Kind;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Thời gian nghỉ của provider theo loại lỗi. */
class ProviderHealthTest {

    @Test
    void cooldownFollowsTheProviderAdviceAndIsCapped() {
        assertThat(ProviderHealth.cooldown(new AiProviderException(Kind.QUOTA, "x", Duration.ofSeconds(10), false))).isEqualTo(Duration.ofSeconds(12));
        assertThat(ProviderHealth.cooldown(new AiProviderException(Kind.QUOTA, "x", null, true))).isEqualTo(ProviderHealth.QUOTA_DAILY);
        assertThat(ProviderHealth.cooldown(new AiProviderException(Kind.QUOTA, "x", Duration.ofDays(2), false))).isEqualTo(ProviderHealth.MAX_COOLDOWN);
        assertThat(ProviderHealth.cooldown(new AiProviderException(Kind.OVERLOADED, "x", null, false))).isEqualTo(ProviderHealth.OVERLOADED_DEFAULT);
    }

    @Test
    void errorStaysVisibleUntilTheProviderHasWorkedAtLeastOnce() {
        var health = new ProviderHealth();
        health.failure(new AiProviderException(Kind.OTHER, "x", null, false));

        assertThat(health.snapshot().failing()).isTrue();
        assertThat(health.snapshot().cooling()).isFalse();
        health.success();
        assertThat(health.snapshot().failing()).isFalse();
    }

    @Test
    void checkingFlagIsReportedUntilCleared() {
        var health = new ProviderHealth();
        health.checking(true);
        assertThat(health.checking()).isTrue();
        health.checking(false);
        assertThat(health.checking()).isFalse();
    }
}
