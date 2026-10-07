package com.ctv.flowtracer.adapter.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.ir.SourceRole;
import org.junit.jupiter.api.Test;

class SpringStackAdapterTest {

    @Test
    void identity() {
        var adapter = new SpringStackAdapter();
        assertThat(adapter.id()).isEqualTo("spring");
        assertThat(adapter.roles()).containsExactly(SourceRole.BACKEND);
        assertThat(adapter.defaultNoiseFilters()).isNotEmpty();
    }
}
