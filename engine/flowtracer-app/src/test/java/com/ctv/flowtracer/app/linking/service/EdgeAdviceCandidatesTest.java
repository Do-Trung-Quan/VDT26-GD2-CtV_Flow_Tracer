package com.ctv.flowtracer.app.linking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.app.linking.dto.response.EdgeCandidateResponse;
import com.ctv.flowtracer.app.linking.dto.response.EdgeDetailResponse;
import com.ctv.flowtracer.app.linking.dto.response.EdgeNodeResponse;
import com.ctv.flowtracer.core.linker.CandidateScorer;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.NodeKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ứng viên đưa cho AI: liên kết suy luận chỉ khớp một API thì đích tự động là ứng viên duy nhất. */
class EdgeAdviceCandidatesTest {

    private static EdgeDetailResponse detail(EdgeNodeResponse to, List<EdgeCandidateResponse> candidates) {
        var from = new EdgeNodeResponse("ng:http:1", NodeKind.HTTP_CALL, "POST /api/x", null, null, null);
        return new EdgeDetailResponse("e1", EdgeKind.HTTP_REQUEST, Confidence.INFERRED, "suy luận", from, to, candidates, null, null, null, true);
    }

    @Test
    void theAutoTargetBecomesTheOnlyCandidateWhenNoCandidateListIsStored() {
        var to = new EdgeNodeResponse("java:endpoint:POST /api/x", NodeKind.API_ENDPOINT, "POST /api/x", null, null, null);

        List<EdgeCandidateResponse> result = EdgeAdviceService.candidates(detail(to, List.of()));

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.nodeKey()).isEqualTo("java:endpoint:POST /api/x");
            assertThat(c.current()).isTrue();
        });
    }

    @Test
    void storedCandidatesAreUsedAsIs() {
        var a = new EdgeCandidateResponse(null, "k1", CandidateScorer.Level.HIGH, "Cao", "r", false, List.of());
        var to = new EdgeNodeResponse("k1", NodeKind.API_ENDPOINT, "GET /a", null, null, null);

        assertThat(EdgeAdviceService.candidates(detail(to, List.of(a)))).containsExactly(a);
    }

    @Test
    void noTargetAndNoCandidatesMeansNothingToAnalyse() {
        assertThat(EdgeAdviceService.candidates(detail(null, List.of()))).isEmpty();
    }
}
