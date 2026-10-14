package com.ctv.flowtracer.app.ai.service;

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
import com.ctv.flowtracer.app.common.error.AppException;
import com.ctv.flowtracer.app.common.error.ErrorCode;
import com.ctv.flowtracer.app.config.FlowTracerProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * CỔNG AI DUY NHẤT (CLAUDE.md §3.6). Mọi nội dung AI đi qua 7 bước:
 * <ol>
 *   <li>mục đích có bật không;
 *   <li>bản đã lưu theo mã băm đầu vào (tăng {@code hit_count} khi trúng);
 *   <li>provider khả dụng và KHÔNG đang bị chặn sau lỗi hết hạn mức 429 ({@link ProviderHealth}; lỗi khác không chặn, người dùng bấm lại là thử lại);
 *   <li>hạn mức token trong ngày;
 *   <li>che secret ({@link SecretRedactor});
 *   <li>gọi provider và kiểm tra đầu ra bằng {@link AiCall#validator()};
 *   <li>lưu {@code ai_artifacts} và ghi {@code llm_call_logs} (chỉ mã băm, không lưu prompt).
 * </ol>
 * Không bước nào ném lỗi ra ngoài: không có nội dung AI thì trả {@link AiOutcome} với lý do, người gọi dùng kết quả tất định. Hai
 * yêu cầu giống hệt nhau đang chạy được gộp thành một lời gọi; số lời gọi đồng thời tới provider bị giới hạn để không tự gây 429.
 */
@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);
    private static final int MAX_PROMPT_CHARS = 60_000;
    private static final int MAX_CONCURRENT_CALLS = 2;

    private final LlmProvider provider;
    private final AiSettingsService settings;
    private final AiArtifactRepository artifacts;
    private final LlmCallLogRepository logs;
    private final FlowTracerProperties properties;
    private final JsonMapper json;
    private final ProviderHealth health;
    private final AiCallTrace trace;
    private final SecretRedactor redactor = new SecretRedactor();
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_CALLS);
    private final Map<String, CompletableFuture<AiOutcome>> inFlight = new ConcurrentHashMap<>();

    public AiGateway(
            LlmProvider provider,
            AiSettingsService settings,
            AiArtifactRepository artifacts,
            LlmCallLogRepository logs,
            FlowTracerProperties properties,
            JsonMapper json,
            ProviderHealth health,
            AiCallTrace trace) {
        this.provider = provider;
        this.settings = settings;
        this.artifacts = artifacts;
        this.logs = logs;
        this.properties = properties;
        this.json = json;
        this.health = health;
        this.trace = trace;
    }

    /** Mục đích đang bật VÀ provider dùng được — dùng để quyết định có hiện nút / nhãn AI hay không. */
    public boolean enabled(AiPurpose purpose) {
        return settings.isEnabled(purpose) && provider.available();
    }

    /**
     * Chỉ xem bản đã lưu, không gọi AI. Mục đích bị tắt thì coi như không có (người dùng đã chọn dùng nội dung tất định).
     * Trúng thì ghi nhận một lượt dùng lại.
     */
    public Optional<AiOutcome> peek(AiCall call) {
        if (!settings.isEnabled(call.purpose())) {
            return Optional.empty();
        }
        return cached(call).map(a -> {
            record(a);
            return new AiOutcome(AiOutcome.Status.CACHED, a.getContent(), null, a.getProvider(), a.getModel());
        });
    }

    /** Vì sao AI không dùng được cho mục đích này (để hiện cho người dùng); null nếu đang dùng được. */
    public String offReason(AiPurpose purpose) {
        if (!settings.isEnabled(purpose)) {
            return "Mục đích này đang được tắt trong Cấu hình AI.";
        }
        if (!provider.available()) {
            return "AI chưa được bật (provider = " + provider.providerId() + "): đang dùng nội dung tự sinh từ mã nguồn.";
        }
        ProviderHealth.Snapshot h = health.snapshot();
        if (h.cooling()) {
            return h.message() + " Đang dùng nội dung tự sinh từ mã nguồn.";
        }
        return null;
    }

    /** Bản mới nhất của một đối tượng, bất kể đầu vào đã đổi hay chưa (dùng khi chỉ cần hiển thị, vd tên thành phần). */
    public Optional<Map<String, Object>> latest(UUID workspaceId, AiPurpose purpose, String targetKey) {
        if (!settings.isEnabled(purpose)) {
            return Optional.empty();
        }
        return artifacts.findFirstByWorkspaceIdAndPurposeAndTargetKeyOrderByCreatedAtDesc(workspaceId, purpose, targetKey)
                .map(AiArtifact::getContent);
    }

    /** Bản mới nhất của mọi đối tượng cùng mục đích trong Workspace: khóa đối tượng → nội dung. */
    public Map<String, Map<String, Object>> latestByTarget(UUID workspaceId, AiPurpose purpose) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (!settings.isEnabled(purpose)) {
            return result;
        }
        for (AiArtifact a : artifacts.findByWorkspaceIdAndPurposeOrderByCreatedAtAsc(workspaceId, purpose)) {
            result.put(a.getTargetKey(), a.getContent()); // sắp xếp tăng dần: bản sau ghi đè bản trước
        }
        return result;
    }

    /**
     * Chạy đủ 7 bước.
     *
     * @param force true = bỏ qua bản đã lưu (nút "Sinh lại")
     */
    public AiOutcome execute(AiCall call, boolean force) {
        if (!settings.isEnabled(call.purpose())) {
            return AiOutcome.disabled("Mục đích này đang được tắt trong Cấu hình AI.");
        }
        if (!force) {
            Optional<AiArtifact> hit = cached(call);
            if (hit.isPresent()) {
                record(hit.get());
                AiArtifact a = hit.get();
                return new AiOutcome(AiOutcome.Status.CACHED, a.getContent(), null, a.getProvider(), a.getModel());
            }
        }
        if (!provider.available()) {
            return AiOutcome.disabled(
                    "AI chưa được bật (provider = " + provider.providerId() + "): đang dùng nội dung tự sinh từ mã nguồn.");
        }
        ProviderHealth.Snapshot h = health.snapshot();
        if (h.cooling()) {
            String message = h.message() + " Đang dùng nội dung tự sinh từ mã nguồn.";
            return h.kind() == AiProviderException.Kind.QUOTA ? AiOutcome.quota(message) : AiOutcome.unavailable(message);
        }
        long limit = properties.ai().dailyTokenLimit();
        if (limit > 0 && settings.tokensToday() >= limit) {
            logCall(call, hash(call.system() + "\n" + call.user()), null, 0, LlmCallStatus.QUOTA_EXCEEDED);
            return AiOutcome.quota("Đã hết hạn mức token hôm nay: đang dùng nội dung tự sinh từ mã nguồn.");
        }
        // Gộp các yêu cầu giống hệt đang chạy: người đến sau chờ kết quả của người đến trước, không gọi AI lần nữa
        String key = call.purpose() + "|" + call.targetKey() + "|" + inputHash(call);
        var mine = new CompletableFuture<AiOutcome>();
        CompletableFuture<AiOutcome> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            return running.join();
        }
        try {
            AiOutcome outcome = callProvider(call);
            mine.complete(outcome);
            return outcome;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key);
        }
    }

    private AiOutcome callProvider(AiCall call) {
        String system = redactor.redact(call.system());
        String user = redactor.redact(call.user());
        if (user.length() > MAX_PROMPT_CHARS) {
            user = user.substring(0, MAX_PROMPT_CHARS);
        }
        String promptHash = hash(system + "\n" + user);
        String instruction = call.jsonSchema() == null
                ? user
                : user + "\n\nChỉ trả về MỘT đối tượng JSON hợp lệ (không kèm giải thích, không bọc trong khối mã) theo cấu trúc sau:\n"
                        + call.jsonSchema();
        long start = System.nanoTime();
        LlmResult result;
        slots.acquireUninterruptibly();
        try {
            var request = new LlmRequest(system, instruction, call.jsonSchema(), call.maxOutputTokens());
            trace.request(call.purpose().name(), call.targetKey(), provider.providerId(), properties.ai().model(), request);
            result = provider.complete(request);
        } catch (AiProviderException e) {
            ProviderHealth.Snapshot after = health.failure(e);
            settings.rememberQuotaLimit(e);
            trace.failure(call.purpose().name(), e.kind() + ": " + e.getMessage(), millis(start));
            logCall(call, promptHash, null, millis(start),
                    e.kind() == AiProviderException.Kind.QUOTA ? LlmCallStatus.QUOTA_EXCEEDED : LlmCallStatus.ERROR);
            String message = after.message() + " Đang dùng nội dung tự sinh từ mã nguồn.";
            return e.kind() == AiProviderException.Kind.QUOTA ? AiOutcome.quota(message) : AiOutcome.unavailable(message);
        } catch (RuntimeException e) {
            log.warn("Gọi AI lỗi ({}): {}", call.purpose(), e.getClass().getSimpleName());
            trace.failure(call.purpose().name(), e.getClass().getSimpleName(), millis(start));
            logCall(call, promptHash, null, millis(start), LlmCallStatus.ERROR);
            return AiOutcome.unavailable("AI không phản hồi: đang dùng nội dung tự sinh từ mã nguồn.");
        } finally {
            slots.release();
        }
        health.success();
        int latency = millis(start);
        trace.response(call.purpose().name(), result, latency);
        Map<String, Object> content;
        try {
            content = call.validator().validate(parse(result.content()));
        } catch (AiRejectedException | tools.jackson.core.JacksonException e) {
            log.debug("Đầu ra AI bị loại ({}): {}", call.purpose(), e.getMessage());
            trace.rejected(call.purpose().name(), e.getMessage());
            logCall(call, promptHash, result, latency, LlmCallStatus.REJECTED_BY_VALIDATION);
            return AiOutcome.rejected("Đầu ra của AI không đạt kiểm tra: đang dùng nội dung tự sinh từ mã nguồn.");
        }
        store(call, content, result);
        logCall(call, promptHash, result, latency, LlmCallStatus.OK);
        return new AiOutcome(AiOutcome.Status.GENERATED, content, null, provider.providerId(), result.model());
    }

    // ---------------- lỗi → HTTP 503 ----------------

    /**
     * Lỗi 503 đúng loại cho một kết quả không có nội dung AI (tắt / hết hạn mức / lỗi hoặc bị loại). Khi provider đang nghỉ, kèm
     * {@code retryAt} (thời điểm thử lại) và {@code reason} (loại lỗi) để giao diện hiện banner và đếm ngược.
     */
    public AppException failure(AiOutcome outcome) {
        String text = outcome.message() == null ? "AI không dùng được lúc này." : outcome.message();
        ErrorCode code = switch (outcome.status()) {
            case DISABLED -> ErrorCode.AI_DISABLED;
            case QUOTA_EXCEEDED -> ErrorCode.AI_QUOTA_EXCEEDED;
            default -> ErrorCode.AI_UNAVAILABLE;
        };
        ProviderHealth.Snapshot h = health.snapshot();
        if (h.cooling()) {
            return new AppException(code, text, Map.of("retryAt", h.until().toString(), "reason", String.valueOf(h.kind())));
        }
        return h.failing() ? new AppException(code, text, Map.of("reason", String.valueOf(h.kind()))) : new AppException(code, text);
    }

    // ---------------- hỗ trợ ----------------

    /** Khóa cache: phiên bản prompt + toàn bộ đầu vào (đã là bản chưa che; chỉ băm, không lưu). */
    public static String inputHash(AiCall call) {
        return hash(call.promptVersion() + "\n" + call.system() + "\n" + call.user());
    }

    private Optional<AiArtifact> cached(AiCall call) {
        return artifacts.findByWorkspaceIdAndPurposeAndTargetKeyAndInputHash(
                call.workspaceId(), call.purpose(), call.targetKey(), inputHash(call));
    }

    private void record(AiArtifact artifact) {
        try {
            artifact.recordHit(Instant.now());
            artifacts.save(artifact);
        } catch (RuntimeException e) {
            log.debug("Không ghi được lượt dùng lại bản lưu: {}", e.toString());
        }
    }

    private void store(AiCall call, Map<String, Object> content, LlmResult result) {
        try {
            artifacts.deleteByWorkspaceIdAndPurposeAndTargetKey(call.workspaceId(), call.purpose(), call.targetKey());
            artifacts.save(new AiArtifact(
                    call.workspaceId(), call.targetKey(), call.purpose(), inputHash(call), call.promptVersion(),
                    provider.providerId(), result.model() == null ? "" : result.model(), content));
        } catch (DataIntegrityViolationException e) {
            log.debug("Bản lưu AI đã có (ghi đồng thời): {}", call.targetKey());
        }
    }

    private void logCall(AiCall call, String promptHash, LlmResult result, int latency, LlmCallStatus status) {
        try {
            int in = result == null ? 0 : result.inputTokens();
            int out = result == null ? 0 : result.outputTokens();
            logs.save(new LlmCallLog(
                    call.workspaceId(), call.purpose().name(), provider.providerId(),
                    result == null || result.model() == null ? "" : result.model(), promptHash, in, out, latency, status));
        } catch (RuntimeException e) {
            log.warn("Không ghi được nhật ký gọi AI: {}", e.toString());
        }
    }

    /** Lấy đối tượng JSON đầu tiên trong câu trả lời (bỏ rào ```json … ``` nếu có). */
    Map<String, Object> parse(String raw) {
        String text = raw == null ? "" : raw.strip();
        int from = text.indexOf('{');
        int to = text.lastIndexOf('}');
        if (from < 0 || to <= from) {
            throw new AiRejectedException("Không có đối tượng JSON trong câu trả lời");
        }
        return json.readValue(text.substring(from, to + 1), new TypeReference<Map<String, Object>>() {});
    }

    private static int millis(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000);
    }

    static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Danh sách mục đích MVP — dùng cho trang Cấu hình AI. */
    public static List<AiPurpose> purposes() {
        return List.of(AiPurpose.values()).stream().filter(AiPurpose::isMvp).toList();
    }
}
