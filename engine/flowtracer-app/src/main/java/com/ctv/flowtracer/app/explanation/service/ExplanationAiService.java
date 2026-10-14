package com.ctv.flowtracer.app.explanation.service;

import com.ctv.flowtracer.app.ai.model.AiCall;
import com.ctv.flowtracer.app.ai.model.AiOutcome;
import com.ctv.flowtracer.app.ai.model.AiPurpose;
import com.ctv.flowtracer.app.ai.model.AiRejectedException;
import com.ctv.flowtracer.app.ai.service.AiGateway;
import com.ctv.flowtracer.app.ai.service.PromptTemplates;
import com.ctv.flowtracer.app.explanation.dto.response.AiInfoResponse;
import com.ctv.flowtracer.app.explanation.dto.response.ApiIoResponse;
import com.ctv.flowtracer.app.explanation.dto.response.ExplanationResponse;
import com.ctv.flowtracer.app.explanation.dto.response.MockDataResponse;
import com.ctv.flowtracer.app.explanation.dto.response.PurposeResponse;
import com.ctv.flowtracer.app.explanation.dto.response.ScenarioResponse;
import com.ctv.flowtracer.core.mock.FallbackMockGenerator;
import com.ctv.flowtracer.core.mock.SchemaValidator;
import com.ctv.flowtracer.core.scenario.ScenarioPolishRule;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.scenario.Scenario;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Phần AI của tab Diễn giải (Mục đích + ràng buộc + lỗi, Dữ liệu mẫu) và tab Kịch bản. Quy tắc chung: AI chỉ VIẾT LẠI những gì
 * phân tích tĩnh đã tìm ra — mỗi câu ràng buộc / lỗi phải trỏ về một mục thô có thật (theo id), kịch bản giữ nguyên số bước,
 * dữ liệu mẫu phải khớp schema DTO. Đầu ra không đạt thì bỏ, dùng kết quả tất định ({@code ai.state} cho giao diện biết lý do).
 * Mọi lời gọi AI đi qua {@link AiGateway}.
 */
@Service
public class ExplanationAiService {

    public static final String AI_LABEL = "✦ AI";
    private static final int MIN_PURPOSE_WORDS = 40;
    private static final int MAX_PURPOSE_WORDS = 140;
    private static final int MAX_SENTENCE = 400;

    private final AiGateway gateway;
    private final PromptTemplates prompts;
    private final JsonMapper json;

    public ExplanationAiService(AiGateway gateway, PromptTemplates prompts, JsonMapper json) {
        this.gateway = gateway;
        this.prompts = prompts;
        this.json = json;
    }

    // =====================================================================================================================
    // PURPOSE_TEXT
    // =====================================================================================================================

    /** Dùng bản AI đã lưu nếu có (không gọi AI); không có thì giữ nội dung tất định và báo trạng thái. */
    public ExplanationResponse applyPurpose(UUID workspaceId, ExplanationResponse base) {
        if (base.container()) {
            return base;
        }
        Optional<AiOutcome> hit = gateway.peek(purposeCall(workspaceId, base));
        if (hit.isPresent()) {
            return merge(base, hit.get());
        }
        String off = gateway.offReason(AiPurpose.PURPOSE_TEXT);
        return withInfo(base, off != null ? AiInfoResponse.off(off) : AiInfoResponse.missing());
    }

    /** Gọi AI (hoặc dùng bản lưu nếu {@code force} = false). */
    public AiOutcome generatePurpose(UUID workspaceId, ExplanationResponse base, boolean force) {
        return gateway.execute(purposeCall(workspaceId, base), force);
    }

    AiCall purposeCall(UUID workspaceId, ExplanationResponse base) {
        var facts = new StringBuilder();
        // Mã API ngắn (a1, a2…) thay cho key thật: tên "apiKey" trông như khóa bí mật nên bị che trước khi gửi tới AI
        Map<String, Set<String>> knownIds = new LinkedHashMap<>();
        Map<String, String> apiByCode = new LinkedHashMap<>();
        for (ApiIoResponse api : base.apis()) {
            String code = "a" + (apiByCode.size() + 1);
            apiByCode.put(code, api.apiKey());
            knownIds.put(code, ids(api));
            facts.append("- mã: ").append(code).append('\n')
                    .append("  API: ").append(api.method()).append(' ').append(api.path())
                    .append(api.displayName() == null ? "" : " — " + api.displayName()).append('\n')
                    .append("  Đầu vào: ").append(compact(api.input())).append('\n')
                    .append("  Đầu ra: ").append(compact(api.output())).append('\n');
            appendList(facts, "  Ràng buộc", "c", api.constraints());
            appendList(facts, "  Lỗi có thể gặp", "e", api.errors());
        }
        if (base.apis().isEmpty()) {
            facts.append("(Thành phần này không gọi API nào")
                    .append(base.unresolvedCalls() == null || base.unresolvedCalls().isEmpty() ? "" : ", trừ các lời gọi chưa xác định: " + base.unresolvedCalls())
                    .append(").\n");
        }
        if (base.callsTo() != null && !base.callsTo().isEmpty()) {
            facts.append("Gọi tới: ").append(base.callsTo().stream().map(r -> r.displayName() != null ? r.displayName() : r.name())
                    .collect(Collectors.joining("; "))).append('\n');
        }
        String kind = "API_ENDPOINT".equals(String.valueOf(base.kind())) ? "API" : "phần tử giao diện";
        var rendered = prompts.render("purpose-text.v1", Map.of(
                "kind", kind,
                "name", base.displayName() != null ? base.displayName() : base.name(),
                "source", base.purpose().sourceLabel(),
                "skeleton", base.purpose().text(),
                "facts", facts.toString()));
        return new AiCall(workspaceId, AiPurpose.PURPOSE_TEXT, base.nodeKey(), rendered.version(), rendered.system(), rendered.user(),
                rendered.schema(), 2500, raw -> validatePurpose(raw, knownIds, apiByCode));
    }

    private Map<String, Object> validatePurpose(Map<String, Object> raw, Map<String, Set<String>> knownIds, Map<String, String> apiByCode) {
        if (!(raw.get("purpose") instanceof String purpose) || purpose.isBlank()) {
            throw new AiRejectedException("Thiếu đoạn mục đích");
        }
        int words = purpose.strip().split("\\s+").length;
        if (words < MIN_PURPOSE_WORDS || words > MAX_PURPOSE_WORDS) {
            throw new AiRejectedException("Đoạn mục đích " + words + " từ, ngoài khoảng cho phép");
        }
        List<Map<String, Object>> apis = new ArrayList<>();
        if (raw.get("apis") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m) || !(m.get("api") instanceof String key) || !knownIds.containsKey(key)) {
                    continue; // API không có trong đầu vào: bỏ
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("apiKey", apiByCode.get(key));
                entry.put("constraints", sentences(m.get("constraints"), knownIds.get(key), "c"));
                entry.put("errors", sentences(m.get("errors"), knownIds.get(key), "e"));
                apis.add(entry);
            }
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("purpose", purpose.strip());
        content.put("apis", apis);
        return content;
    }

    /** Chỉ giữ câu có id thuộc đầu vào (trỏ về một mục thô có thật) và độ dài hợp lý. */
    private static List<Map<String, Object>> sentences(Object value, Set<String> known, String prefix) {
        var out = new ArrayList<Map<String, Object>>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("id") instanceof String id && known.contains(id) && id.startsWith(prefix)
                        && m.get("text") instanceof String text && !text.isBlank() && text.length() <= MAX_SENTENCE) {
                    out.add(Map.of("id", id, "text", text.strip()));
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private ExplanationResponse merge(ExplanationResponse base, AiOutcome outcome) {
        Map<String, Object> content = outcome.content();
        Map<String, Map<String, String>> byApi = new LinkedHashMap<>();
        for (Map<String, Object> api : (List<Map<String, Object>>) content.getOrDefault("apis", List.of())) {
            Map<String, String> texts = new LinkedHashMap<>();
            for (String field : List.of("constraints", "errors")) {
                for (Map<String, Object> s : (List<Map<String, Object>>) api.getOrDefault(field, List.of())) {
                    texts.put(String.valueOf(s.get("id")), String.valueOf(s.get("text")));
                }
            }
            byApi.put(String.valueOf(api.get("apiKey")), texts);
        }
        List<ApiIoResponse> apis = base.apis().stream()
                .map(a -> {
                    Map<String, String> t = byApi.getOrDefault(a.apiKey(), Map.of());
                    return new ApiIoResponse(a.apiKey(), a.method(), a.path(), a.displayName(), a.input(), a.output(),
                            replace(a.errors(), "e", t), replace(a.constraints(), "c", t));
                })
                .toList();
        return new ExplanationResponse(
                base.nodeKey(), base.kind(), base.name(), base.displayName(), base.container(),
                new PurposeResponse(String.valueOf(content.get("purpose")), "AI", AI_LABEL),
                apis, base.unresolvedCalls(), base.trail(), base.calledFrom(), base.calledFromTotal(), base.callsTo(),
                AiInfoResponse.applied(outcome.provider(), outcome.model()));
    }

    /** Thay câu thô bằng câu AI cùng id; id nào AI không trả thì giữ câu tất định. */
    private static List<String> replace(List<String> raw, String prefix, Map<String, String> ai) {
        var out = new ArrayList<String>();
        for (int i = 0; i < raw.size(); i++) {
            out.add(ai.getOrDefault(prefix + (i + 1), raw.get(i)));
        }
        return out;
    }

    private static Set<String> ids(ApiIoResponse api) {
        var ids = new java.util.LinkedHashSet<String>();
        for (int i = 1; i <= api.constraints().size(); i++) {
            ids.add("c" + i);
        }
        for (int i = 1; i <= api.errors().size(); i++) {
            ids.add("e" + i);
        }
        return ids;
    }

    private static void appendList(StringBuilder out, String title, String prefix, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        out.append(title).append(":\n");
        for (int i = 0; i < items.size(); i++) {
            out.append("    ").append(prefix).append(i + 1).append(": ").append(items.get(i)).append('\n');
        }
    }

    private static ExplanationResponse withInfo(ExplanationResponse r, AiInfoResponse info) {
        return new ExplanationResponse(r.nodeKey(), r.kind(), r.name(), r.displayName(), r.container(), r.purpose(), r.apis(),
                r.unresolvedCalls(), r.trail(), r.calledFrom(), r.calledFromTotal(), r.callsTo(), info);
    }

    // =====================================================================================================================
    // MOCK_DATA
    // =====================================================================================================================

    public MockDataResponse applyMock(UUID workspaceId, IrNode api, FallbackMockGenerator.Schemas schemas, MockDataResponse base) {
        Optional<AiOutcome> hit = gateway.peek(mockCall(workspaceId, api, schemas));
        if (hit.isPresent()) {
            AiOutcome o = hit.get();
            return new MockDataResponse(base.apiKey(), o.content().get("request"), o.content().get("response"), "AI", List.of(),
                    AiInfoResponse.applied(o.provider(), o.model()));
        }
        String off = gateway.offReason(AiPurpose.MOCK_DATA);
        return withInfo(base, off != null ? AiInfoResponse.off(off) : AiInfoResponse.missing());
    }

    public AiOutcome generateMock(UUID workspaceId, IrNode api, FallbackMockGenerator.Schemas schemas, boolean force) {
        return gateway.execute(mockCall(workspaceId, api, schemas), force);
    }

    private AiCall mockCall(UUID workspaceId, IrNode api, FallbackMockGenerator.Schemas schemas) {
        String requestSchema = schemas.request() == null ? "(không có)" : write(schemas.request());
        String responseSchema = schemas.response() == null ? "(không có)" : write(schemas.response());
        var rendered = prompts.render("mock-data.v1", Map.of(
                "api", api.name(),
                "summary", api.displayName() == null ? "(không có)" : api.displayName(),
                "requestSchema", requestSchema,
                "responseSchema", responseSchema));
        return new AiCall(workspaceId, AiPurpose.MOCK_DATA, api.key(), rendered.version(), rendered.system(), rendered.user(),
                rendered.schema(), 3000, raw -> validateMock(raw, schemas));
    }

    private Map<String, Object> validateMock(Map<String, Object> raw, FallbackMockGenerator.Schemas schemas) {
        var validator = new SchemaValidator();
        Object request = raw.get("request");
        Object response = raw.get("response");
        if (schemas.request() != null) {
            List<String> errors = validator.validate(write(schemas.request()), write(request));
            if (!errors.isEmpty()) {
                throw new AiRejectedException("request không khớp schema: " + errors.getFirst());
            }
        } else {
            request = Map.of();
        }
        if (schemas.response() != null) {
            List<String> errors = response == null ? List.of("thiếu response") : validator.validate(write(schemas.response()), write(response));
            if (!errors.isEmpty()) {
                throw new AiRejectedException("response không khớp schema: " + errors.getFirst());
            }
        } else {
            response = null;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("request", request);
        content.put("response", response);
        return content;
    }

    private static MockDataResponse withInfo(MockDataResponse r, AiInfoResponse info) {
        return new MockDataResponse(r.apiKey(), r.request(), r.response(), r.source(), r.problems(), info);
    }

    // =====================================================================================================================
    // SCENARIO_POLISH
    // =====================================================================================================================

    public ScenarioResponse applyScenario(UUID workspaceId, Scenario framework, ScenarioResponse base) {
        Optional<AiOutcome> hit = gateway.peek(scenarioCall(workspaceId, framework, base.traceId(), base.title()));
        if (hit.isPresent()) {
            AiOutcome o = hit.get();
            return base.with(AiInfoResponse.applied(o.provider(), o.model()), polished(o.content()));
        }
        String off = gateway.offReason(AiPurpose.SCENARIO_POLISH);
        return base.with(off != null ? AiInfoResponse.off(off) : AiInfoResponse.missing(), null);
    }

    public AiOutcome generateScenario(UUID workspaceId, Scenario framework, String traceId, String title, boolean force) {
        return gateway.execute(scenarioCall(workspaceId, framework, traceId, title), force);
    }

    private AiCall scenarioCall(UUID workspaceId, Scenario framework, String traceId, String title) {
        String actors = framework.actors().stream().map(a -> "- " + a.name() + ": " + a.description()).collect(Collectors.joining("\n"));
        String steps = framework.items().stream()
                .map(i -> i.index() == null ? "(" + i.text() + ")" : i.index() + ": " + i.text())
                .collect(Collectors.joining("\n"));
        var rendered = prompts.render("scenario-polish.v1", Map.of("title", title == null ? "" : title, "actors", actors, "steps", steps));
        return new AiCall(workspaceId, AiPurpose.SCENARIO_POLISH, traceId, rendered.version(), rendered.system(), rendered.user(),
                rendered.schema(), 4000, raw -> validateScenario(raw, framework));
    }

    private static Map<String, Object> validateScenario(Map<String, Object> raw, Scenario framework) {
        var entries = new ArrayList<Map.Entry<Integer, String>>();
        if (raw.get("items") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("index") instanceof Number n && m.get("text") instanceof String t) {
                    entries.add(Map.entry(n.intValue(), t));
                }
            }
        }
        try {
            Map<Integer, String> ok = new ScenarioPolishRule().apply(framework, entries);
            var items = new ArrayList<Map<String, Object>>();
            ok.forEach((index, text) -> items.add(Map.of("index", index, "text", text)));
            return Map.of("items", items);
        } catch (ScenarioPolishRule.Rejected e) {
            throw new AiRejectedException(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, String> polished(Map<String, Object> content) {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (Map<String, Object> item : (List<Map<String, Object>>) content.getOrDefault("items", List.of())) {
            map.put(((Number) item.get("index")).intValue(), String.valueOf(item.get("text")));
        }
        return map;
    }

    // ---------------- hỗ trợ ----------------

    private String compact(Object value) {
        return value == null ? "(không có)" : write(value);
    }

    private String write(Object value) {
        return json.writeValueAsString(value);
    }
}
