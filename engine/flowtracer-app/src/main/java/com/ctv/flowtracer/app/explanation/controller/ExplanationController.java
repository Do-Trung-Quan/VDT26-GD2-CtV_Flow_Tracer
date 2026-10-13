package com.ctv.flowtracer.app.explanation.controller;

import com.ctv.flowtracer.app.common.web.ApiResponse;
import com.ctv.flowtracer.app.common.web.NodeKeyCodec;
import com.ctv.flowtracer.app.explanation.dto.response.ExplanationResponse;
import com.ctv.flowtracer.app.explanation.dto.response.MockDataResponse;
import com.ctv.flowtracer.app.explanation.dto.response.ScenarioResponse;
import com.ctv.flowtracer.app.explanation.service.ExplanationService;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API F1–F3: nội dung tab Diễn giải, dữ liệu mẫu và tab Kịch bản. {@code nodeKey} trên đường dẫn được mã hóa bằng
 * {@link NodeKeyCodec} rồi mã hóa URL thêm một lần (key chứa dấu {@code /}).
 */
@RestController
@RequestMapping("/workspaces/{id}")
public class ExplanationController {

    private final ExplanationService explanationService;

    public ExplanationController(ExplanationService explanationService) {
        this.explanationService = explanationService;
    }

    /** F1 — phần tất định trả ngay; AI chưa tham gia. */
    @GetMapping("/nodes/{nodeKey}/explanation")
    public ApiResponse<ExplanationResponse> explanation(@PathVariable UUID id, @PathVariable String nodeKey) {
        return ApiResponse.ok(explanationService.explain(id, NodeKeyCodec.decode(nodeKey)));
    }

    /** F2 — dữ liệu mẫu của một API; thành phần giao diện gọi nhiều API thì chọn bằng {@code apiKey}. */
    @GetMapping("/nodes/{nodeKey}/mock-data")
    public ApiResponse<MockDataResponse> mockData(
            @PathVariable UUID id,
            @PathVariable String nodeKey,
            @RequestParam(required = false) @Size(max = 1000, message = "Mã API tối đa 1000 ký tự") String apiKey) {
        return ApiResponse.ok(explanationService.mockData(id, NodeKeyCodec.decode(nodeKey), apiKey));
    }

    /** F3 — kịch bản của luồng đã dò bằng D1; luồng hết hạn trong bộ nhớ đệm thì {@code 404 TRACE_NOT_FOUND}. */
    @GetMapping("/traces/{traceId}/scenario")
    public ApiResponse<ScenarioResponse> scenario(@PathVariable UUID id, @PathVariable String traceId) {
        return ApiResponse.ok(explanationService.scenario(id, traceId));
    }
}
