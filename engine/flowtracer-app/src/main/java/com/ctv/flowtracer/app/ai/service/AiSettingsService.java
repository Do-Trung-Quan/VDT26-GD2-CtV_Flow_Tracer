package com.ctv.flowtracer.app.ai.service;

import com.ctv.flowtracer.app.ai.dto.request.UpdateAiSettingsRequest;
import com.ctv.flowtracer.app.ai.dto.response.AiPurposeSettingResponse;
import com.ctv.flowtracer.app.ai.dto.response.AiSettingsResponse;
import com.ctv.flowtracer.app.ai.dto.response.AiTestResponse;
import com.ctv.flowtracer.app.ai.dto.response.AiUsageResponse;
import com.ctv.flowtracer.app.ai.entity.AppSetting;
import com.ctv.flowtracer.app.ai.entity.LlmCallLog;
import com.ctv.flowtracer.app.ai.entity.LlmCallStatus;
import com.ctv.flowtracer.app.ai.model.AiPurpose;
import com.ctv.flowtracer.app.ai.provider.AiProviderException;
import com.ctv.flowtracer.app.ai.provider.LlmProvider;
import com.ctv.flowtracer.app.ai.provider.LlmRequest;
import com.ctv.flowtracer.app.ai.provider.LlmResult;
import com.ctv.flowtracer.app.ai.repository.AppSettingRepository;
import com.ctv.flowtracer.app.ai.repository.LlmCallLogRepository;
import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.config.FlowTracerProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Cấu hình AI (API I1–I3, I6): bật / tắt từng mục đích (lưu trong {@code app_settings}), kiểm tra kết nối, hạn mức và thống
 * kê. Provider, model và khóa API chỉ đọc từ biến môi trường — không sửa được qua API, không bao giờ trả về.
 */
@Service
public class AiSettingsService {

    /** Tên hiển thị và mô tả của từng mục đích (tiếng Việt), theo trang Cấu hình AI của bản vẽ giao diện. */
    private static final Map<AiPurpose, String[]> TEXTS = Map.of(
            AiPurpose.PURPOSE_TEXT,
            new String[] {"Viết Mục đích & ràng buộc", "Khi bấm \"Sinh bằng AI\" ở tab Diễn giải: viết mục đích 80–100 từ dễ hiểu, diễn đạt lại ràng buộc và \"Lỗi có thể gặp\"; mỗi câu phải trỏ về một ràng buộc / lỗi có thật trong mã nguồn."},
            AiPurpose.SCENARIO_POLISH,
            new String[] {"Viết lời Kịch bản", "Khi bấm \"Sinh bằng AI\" ở tab Kịch bản: viết lại lời kể cho từng bước; giữ nguyên số bước, thứ tự và tên thành phần (lệch thì dùng câu khung)."},
            AiPurpose.MOCK_DATA,
            new String[] {"Sinh Dữ liệu mẫu", "Khi bấm \"Sinh bằng AI\" ở mục Dữ liệu mẫu: sinh một lần từ cấu trúc DTO (JSON Schema) rồi lưu lại, không dùng dữ liệu thật; kết quả phải khớp schema."},
            AiPurpose.EDGE_RESOLUTION,
            new String[] {"Phân tích liên kết", "Khi bấm \"Nhờ AI phân tích\" ở một liên kết chưa chắc chắn: đọc chuỗi gọi từ thao tác của bạn tới điểm gọi (tên hàm, chuỗi truyền vào) rồi đánh giá từng ứng viên (nên chọn / không nên / chưa chắc) kèm bằng chứng, có thể gợi ý thêm một thành phần có thật; không tự đổi liên kết, bạn chốt tay."},
            AiPurpose.ELEMENT_NAMING,
            new String[] {"Đặt tên thành phần giao diện", "Khi bấm \"Đặt tên bằng AI\" ở một màn hình / hộp thoại: đặt tên tiếng Việt ngắn (tối đa 6 từ) cho các nút, ô nhập… chưa có chữ rõ nghĩa, dựa vào chữ trên giao diện, tên hàm và API mà thành phần gọi; chỉ đổi cách hiển thị tên."});

    /** Mã mục đích ghi trong nhật ký cho lần "Kiểm tra kết nối" (không thuộc {@link AiPurpose}) và tên hiển thị của nó. */
    static final String CONNECTION_TEST = "CONNECTION_TEST";

    private static final String CONNECTION_TEST_LABEL = "Kiểm tra kết nối AI";

    /** Thời gian chờ tối đa của "Kiểm tra kết nối". */
    private static final java.time.Duration TEST_TIMEOUT = java.time.Duration.ofSeconds(15);

    private static final java.util.concurrent.ExecutorService TEST_EXECUTOR = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    private final AppSettingRepository settings;
    private final LlmProvider provider;
    private final FlowTracerProperties properties;
    private final LlmCallLogRepository logs;
    private final ProviderHealth health;
    private final AiCallTrace trace;

    public AiSettingsService(
            AppSettingRepository settings,
            LlmProvider provider,
            FlowTracerProperties properties,
            LlmCallLogRepository logs,
            ProviderHealth health,
            AiCallTrace trace) {
        this.settings = settings;
        this.provider = provider;
        this.properties = properties;
        this.logs = logs;
        this.health = health;
        this.trace = trace;
    }

    // ---------------- I1, I2 ----------------

    public AiSettingsResponse view() {
        Map<AiPurpose, Boolean> enabled = purposes();
        List<AiPurposeSettingResponse> list = AiGateway.purposes().stream()
                .map(p -> new AiPurposeSettingResponse(p.name(), TEXTS.get(p)[0], TEXTS.get(p)[1], enabled.get(p)))
                .toList();
        String id = provider.providerId();
        ProviderHealth.Snapshot cooling = health.snapshot();
        String status = "none".equals(id) ? "OFF"
                : !provider.available() ? "NOT_CONFIGURED"
                : cooling.cooling() ? "COOLING"
                : cooling.failing() ? (cooling.kind() == AiProviderException.Kind.OVERLOADED ? "OVERLOADED" : "ERROR")
                : health.checking() ? "CHECKING"
                : "READY";
        String message = switch (status) {
            case "OFF" -> "AI đang tắt (LLM_PROVIDER=none): mọi tính năng vẫn chạy bằng nội dung tự sinh từ mã nguồn.";
            case "READY" -> "Sẵn sàng.";
            case "CHECKING" -> "Đang kiểm tra kết nối tới nhà cung cấp AI…";
            case "COOLING", "OVERLOADED", "ERROR" -> cooling.message();
            default -> "Chưa cấu hình đủ: thiếu khóa API trong biến môi trường " + keyVariable(id)
                    + (id.equals("openai-compatible") ? " hoặc LLM_BASE_URL / LLM_MODEL." : ".");
        };
        return new AiSettingsResponse(
                id,
                "none".equals(id) ? "" : model(),
                status,
                message,
                "COOLING".equals(status) ? cooling.until().toString() : null,
                cooling.failing() && provider.available() && !"none".equals(id) ? cooling.kind().name() : null,
                "none".equals(id) ? null : keyVariable(id),
                properties.ai().dailyTokenLimit(),
                tokensToday(),
                requestsToday(),
                dailyRequestLimit(),
                list);
    }

    /**
     * Ghi nhớ hạn mức lượt gọi theo ngày mà nhà cung cấp báo trong lỗi 429 (vd gói miễn phí: 20 lượt / ngày / model). Đây là con số
     * DUY NHẤT lấy từ nhà cung cấp — Google không công bố trước hạn mức theo ngày của từng gói, chỉ báo khi chạm giới hạn.
     */
    public void rememberQuotaLimit(AiProviderException e) {
        if (e.kind() != AiProviderException.Kind.QUOTA || !e.daily() || e.quotaLimit() == null) {
            return;
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("limit", e.quotaLimit());
        value.put("model", model());
        AppSetting row = settings.findById(AppSetting.AI_DAILY_REQUEST_LIMIT).orElseGet(() -> new AppSetting(AppSetting.AI_DAILY_REQUEST_LIMIT, value));
        row.setValue(value);
        settings.save(row);
    }

    /** Hạn mức lượt gọi theo ngày của model đang dùng, theo lần báo gần nhất của nhà cung cấp; 0 nếu chưa biết. */
    public long dailyRequestLimit() {
        Map<String, Object> saved = settings.findById(AppSetting.AI_DAILY_REQUEST_LIMIT).map(AppSetting::getValue).orElse(Map.of());
        return saved.get("limit") instanceof Number n && model().equals(saved.get("model")) ? n.longValue() : 0;
    }

    /** Số lượt gọi provider đã xử lý hôm nay (giờ máy) — xấp xỉ số yêu cầu nhà cung cấp đã tính. */
    public long requestsToday() {
        return logs.requestsSince(startOfToday());
    }

    public AiSettingsResponse update(UpdateAiSettingsRequest request) {
        Map<AiPurpose, Boolean> current = purposes();
        for (var e : request.purposes().entrySet()) {
            AiPurpose p = parse(e.getKey());
            if (p == null) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Mục đích không hợp lệ: " + e.getKey());
            }
            if (!p.isMvp()) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Mục đích " + e.getKey() + " chưa có trong phiên bản này");
            }
            current.put(p, e.getValue());
        }
        Map<String, Object> value = new LinkedHashMap<>();
        current.forEach((p, on) -> value.put(p.name(), on));
        AppSetting row = settings.findById(AppSetting.AI_PURPOSES).orElseGet(() -> new AppSetting(AppSetting.AI_PURPOSES, value));
        row.setValue(value);
        settings.save(row);
        return view();
    }

    /** Trạng thái từng mục đích MVP; chưa lưu thì mặc định bật. */
    public Map<AiPurpose, Boolean> purposes() {
        Map<AiPurpose, Boolean> result = new LinkedHashMap<>();
        Map<String, Object> saved = settings.findById(AppSetting.AI_PURPOSES).map(AppSetting::getValue).orElse(Map.of());
        for (AiPurpose p : AiGateway.purposes()) {
            result.put(p, !Boolean.FALSE.equals(saved.get(p.name())));
        }
        return result;
    }

    public boolean isEnabled(AiPurpose purpose) {
        return purposes().getOrDefault(purpose, false);
    }

    // ---------------- I3 ----------------

    public AiTestResponse test() {
        if (!provider.available()) {
            return new AiTestResponse(false, 0, view().statusMessage());
        }
        long start = System.nanoTime();
        var request = new LlmRequest("Trả lời ngắn gọn.", "Trả lời đúng một từ: OK", null, 16);
        try {
            trace.request(CONNECTION_TEST, null, provider.providerId(), model(), request);
            // Quá TEST_TIMEOUT thì báo ngay, không để người dùng nhìn màn hình đứng im (lời gọi chạy nền tự kết thúc theo timeout của SDK)
            var call = java.util.concurrent.CompletableFuture.supplyAsync(() -> provider.complete(request), TEST_EXECUTOR);
            LlmResult result;
            try {
                result = call.get(TEST_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                int waited = (int) ((System.nanoTime() - start) / 1_000_000);
                trace.failure(CONNECTION_TEST, "không trả lời sau " + TEST_TIMEOUT.toSeconds() + " giây", waited);
                logs.save(new LlmCallLog(null, CONNECTION_TEST, provider.providerId(), model(), AiGateway.hash("connection-test"), 0, 0, waited, LlmCallStatus.ERROR));
                return new AiTestResponse(false, waited, provider.providerId() + " không trả lời sau " + TEST_TIMEOUT.toSeconds()
                        + " giây. Vui lòng kiểm tra mạng, khóa API và model.");
            } catch (java.util.concurrent.ExecutionException e) {
                throw e.getCause() instanceof RuntimeException r ? r : new IllegalStateException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            int ms = (int) ((System.nanoTime() - start) / 1_000_000);
            health.success();
            trace.response(CONNECTION_TEST, result, ms);
            // Ghi token thật do nhà cung cấp trả về (trước đây ghi cứng 0 nên cột Token của dòng này luôn bằng 0)
            logs.save(new LlmCallLog(null, CONNECTION_TEST, provider.providerId(), result.model() == null ? model() : result.model(),
                    AiGateway.hash("connection-test"), result.inputTokens(), result.outputTokens(), ms, LlmCallStatus.OK));
            return new AiTestResponse(true, ms, "Kết nối thành công.");
        } catch (RuntimeException e) {
            int ms = (int) ((System.nanoTime() - start) / 1_000_000);
            boolean quota = e instanceof AiProviderException p && p.kind() == AiProviderException.Kind.QUOTA;
            trace.failure(CONNECTION_TEST, e instanceof AiProviderException p ? p.kind() + ": " + p.getMessage() : e.getClass().getSimpleName(), ms);
            logs.save(new LlmCallLog(null, CONNECTION_TEST, provider.providerId(), model(), AiGateway.hash("connection-test"), 0, 0, ms,
                    quota ? LlmCallStatus.QUOTA_EXCEEDED : LlmCallStatus.ERROR));
            if (e instanceof AiProviderException p) {
                String message = health.failure(p).message();
                rememberQuotaLimit(p);
                return new AiTestResponse(false, ms, message);
            }
            return new AiTestResponse(false, ms, "Không kết nối được tới " + provider.providerId() + " (" + e.getClass().getSimpleName()
                    + "). Kiểm tra khóa API, model và mạng.");
        } finally {
            health.checking(false);
        }
    }

    // ---------------- I6 và hạn mức ----------------

    public long tokensToday() {
        return logs.tokensSince(startOfToday());
    }

    public AiUsageResponse usage(LocalDate from, LocalDate to, UUID workspaceId) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate begin = from == null ? end.minusDays(6) : from;
        if (begin.isAfter(end)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        }
        ZoneId zone = ZoneId.systemDefault();
        Instant a = begin.atStartOfDay(zone).toInstant();
        Instant b = end.plusDays(1).atStartOfDay(zone).toInstant();
        long calls = 0;
        long in = 0;
        long out = 0;
        long errors = 0;
        double latencySum = 0;
        var rows = new ArrayList<AiUsageResponse.ByPurpose>();
        for (var r : logs.usage(a, b, workspaceId)) {
            calls += r.getCalls();
            in += r.getInputTokens();
            out += r.getOutputTokens();
            errors += r.getErrors();
            latencySum += r.getAvgLatencyMs() * (r.getCalls() - r.getErrors());
            rows.add(new AiUsageResponse.ByPurpose(
                    r.getPurpose(), labelOf(r.getPurpose()), r.getCalls(), r.getInputTokens() + r.getOutputTokens(), r.getErrors()));
        }
        long ok = calls - errors;
        return new AiUsageResponse(begin, end, calls, in, out, errors, ok > 0 ? Math.round(latencySum / ok) : 0, tokensToday(),
                properties.ai().dailyTokenLimit(), rows);
    }

    // ---------------- hỗ trợ ----------------

    /** Tên hiển thị của mã mục đích trong nhật ký (kể cả {@code CONNECTION_TEST}); mã lạ thì giữ nguyên. */
    private static String labelOf(String code) {
        if (CONNECTION_TEST.equals(code)) {
            return CONNECTION_TEST_LABEL;
        }
        AiPurpose p = parse(code);
        return p == null || !TEXTS.containsKey(p) ? code : TEXTS.get(p)[0];
    }

    private static AiPurpose parse(String name) {
        try {
            return AiPurpose.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String model() {
        return properties.ai().model() == null ? "" : properties.ai().model();
    }

    private static Instant startOfToday() {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    public static String keyVariable(String providerId) {
        return switch (providerId) {
            case "gemini" -> "GEMINI_API_KEY";
            case "anthropic" -> "ANTHROPIC_API_KEY";
            default -> "LLM_API_KEY";
        };
    }
}
