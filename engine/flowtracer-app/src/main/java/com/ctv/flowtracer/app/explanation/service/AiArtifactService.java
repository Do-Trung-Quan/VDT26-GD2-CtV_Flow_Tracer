package com.ctv.flowtracer.app.explanation.service;

import com.ctv.flowtracer.app.ai.model.AiOutcome;
import com.ctv.flowtracer.app.ai.model.AiPurpose;
import com.ctv.flowtracer.app.ai.service.AiGateway;
import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.explanation.dto.request.GenerateAiRequest;
import com.ctv.flowtracer.app.explanation.dto.response.AiGenerationResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * API F4: sinh hoặc sinh lại nội dung AI cho đúng mục đích và đối tượng. AI tắt, hết hạn mức hoặc lỗi thì trả {@code 503} kèm lý do —
 * giao diện giữ nguyên nội dung hiện có. Mục đích {@code EDGE_RESOLUTION} có API riêng (E2).
 */
@Service
public class AiArtifactService {

    private final ExplanationService explanations;
    private final ExplanationAiService ai;
    private final ElementNamingService naming;
    private final AiGateway gateway;

    public AiArtifactService(ExplanationService explanations, ExplanationAiService ai, ElementNamingService naming, AiGateway gateway) {
        this.explanations = explanations;
        this.ai = ai;
        this.naming = naming;
        this.gateway = gateway;
    }

    public AiGenerationResponse generate(UUID workspaceId, GenerateAiRequest request) {
        boolean force = request.force() == null || request.force();
        String target = request.targetKey();
        AiOutcome outcome;
        switch (request.purpose()) {
            case PURPOSE_TEXT -> outcome = ai.generatePurpose(workspaceId, explanations.deterministicExplain(workspaceId, target), force);
            case MOCK_DATA -> {
                var m = explanations.mockContext(workspaceId, target, request.apiKey());
                outcome = ai.generateMock(workspaceId, m.api(), m.schemas(), force);
            }
            case SCENARIO_POLISH -> {
                var c = explanations.scenarioContext(workspaceId, target);
                outcome = ai.generateScenario(workspaceId, c.framework(), target, c.title(), force);
            }
            case ELEMENT_NAMING -> {
                var r = naming.name(workspaceId, target, force);
                if (r.named() == 0 && r.total() > 0) {
                    throw unavailable(r.status(), r.message());
                }
                return new AiGenerationResponse(
                        AiPurpose.ELEMENT_NAMING.name(), r.status().name(), r.provider(), r.model(), r.named(), r.total(), r.message());
            }
            default -> throw new AppException(ErrorCode.VALIDATION_FAILED, "Mục đích " + request.purpose() + " không sinh được qua API này");
        }
        if (!outcome.hasContent()) {
            throw unavailable(outcome.status(), outcome.message());
        }
        return new AiGenerationResponse(request.purpose().name(), outcome.status().name(), outcome.provider(), outcome.model(), null, null, null);
    }

    private AppException unavailable(AiOutcome.Status status, String message) {
        return gateway.failure(new AiOutcome(status, null, message, null, null));
    }
}
