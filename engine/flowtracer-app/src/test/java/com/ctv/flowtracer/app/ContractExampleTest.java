package com.ctv.flowtracer.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.LinkRule;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Hợp đồng Engine ↔ sidecar: ví dụ trong contracts/ phải đọc được thành record IrFragment của Java. */
class ContractExampleTest {

    private static final Path EXAMPLE = Path.of("..", "..", "contracts", "examples", "ir-fragment.example.json");

    @Test
    void exampleDeserializesIntoIrFragment() throws Exception {
        var fragment = JsonMapper.builder().build().readValue(Files.readString(EXAMPLE), IrFragment.class);

        assertThat(fragment.adapterId()).isEqualTo("angular");
        assertThat(fragment.nodes()).hasSize(2);
        assertThat(fragment.edges()).singleElement().satisfies(e -> assertThat(e.confidence()).isEqualTo(Confidence.CERTAIN));
        assertThat(fragment.detectedLinkRules()).singleElement().satisfies(r -> assertThat(r.type()).isEqualTo(LinkRule.Type.REWRITE));
        assertThat(fragment.sorted().nodes().getFirst().key()).startsWith("ng:element:");
    }
}
