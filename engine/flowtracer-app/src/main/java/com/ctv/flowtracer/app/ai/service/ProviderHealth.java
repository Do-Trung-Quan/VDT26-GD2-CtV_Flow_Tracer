package com.ctv.flowtracer.app.ai.service;

import com.ctv.flowtracer.app.ai.provider.AiProviderException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Tình trạng provider AI sau lần gọi gần nhất, theo đúng loại lỗi:
 *
 * <ul>
 *   <li>{@code 429} (hết hạn mức): provider "đạt giới hạn" tới giờ nhà cung cấp báo — trong lúc đó cổng AI KHÔNG gọi provider nữa
 *       (không đốt thêm hạn mức), trả ngay nội dung tự sinh kèm giờ dùng lại.
 *   <li>{@code 503} (quá tải), lỗi kết nối, hết giờ, khóa sai…: chỉ ghi nhận để HIỂN THỊ ("AI quá tải" / "AI bị lỗi") một lúc; KHÔNG chặn
 *       gọi — người dùng bấm lại là thử lại thật.
 *   <li>Một lần gọi thành công đưa mọi thứ về "sẵn sàng".
 * </ul>
 */
@Component
public class ProviderHealth {

    static final Duration QUOTA_DEFAULT = Duration.ofSeconds(60);
    static final Duration QUOTA_DAILY = Duration.ofHours(1);
    static final Duration OVERLOADED_DEFAULT = Duration.ofSeconds(30);
    static final Duration CONFIG_ERROR = Duration.ofMinutes(5);
    static final Duration OTHER_DEFAULT = Duration.ofSeconds(20);
    /** Hạn mức theo ngày có thể phải chờ cả chục giờ: hiện đúng giờ nhà cung cấp báo, tối đa 24 giờ. */
    static final Duration MAX_COOLDOWN = Duration.ofHours(24);
    /** Lỗi không phải 429 chỉ được "nhớ" để hiển thị trong khoảng này, sau đó coi như sẵn sàng trở lại. */
    static final Duration NOTICE_TTL = Duration.ofMinutes(2);

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_DATE = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM");

    /**
     * @param cooling đang bị chặn gọi (chỉ khi hết hạn mức 429)
     * @param kind loại lỗi gần nhất; null khi bình thường
     * @param message câu tiếng Việt nêu lý do; null khi bình thường
     * @param until với {@code cooling}: giờ được gọi lại; ngược lại: lúc thông báo lỗi tự hết hiệu lực; null khi bình thường
     */
    public record Snapshot(boolean cooling, AiProviderException.Kind kind, String message, Instant until) {

        /** Có lỗi gần đây cần hiển thị (kể cả khi không chặn gọi). */
        public boolean failing() {
            return kind != null;
        }
    }

    private static final Snapshot OK = new Snapshot(false, null, null, null);

    private volatile Snapshot current = OK;
    /** Đã có ít nhất một lần gọi provider thành công kể từ khi Engine chạy (chưa có thì "sẵn sàng" chỉ là giả định: mới có khóa API). */
    private volatile boolean verified;
    /** Đang chạy lần kiểm tra kết nối tự động khi khởi động. */
    private volatile boolean checking;

    public Snapshot snapshot() {
        Snapshot s = current;
        // Thông báo lỗi chỉ tự hết hạn khi provider từng chạy được; chưa từng thành công thì giữ lỗi tới khi có một lần gọi thành công
        if (s.failing() && (s.cooling() || verified) && !Instant.now().isBefore(s.until())) {
            current = OK;
            return OK;
        }
        return s;
    }

    public boolean checking() {
        return checking;
    }

    public void checking(boolean value) {
        this.checking = value;
    }

    /** Ghi nhận lỗi provider: 429 → chặn tới giờ nhà cung cấp báo; lỗi khác → chỉ để hiển thị một lúc. */
    public Snapshot failure(AiProviderException e) {
        boolean blocking = e.kind() == AiProviderException.Kind.QUOTA;
        Instant until = Instant.now().plus(blocking ? cooldown(e) : NOTICE_TTL);
        Snapshot s = new Snapshot(blocking, e.kind(), describe(e, until), until);
        current = s;
        return s;
    }

    public void success() {
        verified = true;
        current = OK;
    }

    static Duration cooldown(AiProviderException e) {
        Duration wait = switch (e.kind()) {
            case QUOTA -> e.retryAfter() != null ? e.retryAfter().plusSeconds(2) : e.daily() ? QUOTA_DAILY : QUOTA_DEFAULT;
            case OVERLOADED -> e.retryAfter() != null ? e.retryAfter() : OVERLOADED_DEFAULT;
            case AUTH, BAD_REQUEST -> CONFIG_ERROR;
            case TIMEOUT -> OVERLOADED_DEFAULT;
            case OTHER -> OTHER_DEFAULT;
        };
        return wait.compareTo(MAX_COOLDOWN) > 0 ? MAX_COOLDOWN : wait;
    }

    static String describe(AiProviderException e, Instant until) {
        var local = until.atZone(ZoneId.systemDefault());
        boolean sameDay = local.toLocalDate().equals(LocalDate.now(ZoneId.systemDefault()));
        String when = " Vui lòng thử lại sau " + (sameDay ? TIME : TIME_DATE).format(local) + ".";
        return switch (e.kind()) {
            case QUOTA -> (e.daily() ? "AI đã hết hạn mức theo ngày của nhà cung cấp (429)." : "AI vượt giới hạn số yêu cầu của nhà cung cấp (429).") + when;
            case OVERLOADED -> "Dịch vụ AI đang quá tải (503). Vui lòng thử lại sau ít phút.";
            case AUTH -> "Khóa API bị từ chối (401/403). Vui lòng kiểm tra khóa và quyền dùng model.";
            case BAD_REQUEST -> "Nhà cung cấp từ chối yêu cầu (400). Vui lòng kiểm tra tên model.";
            case TIMEOUT -> "AI trả lời quá lâu nên đã dừng chờ. Vui lòng thử lại.";
            case OTHER -> "AI gặp lỗi khi xử lý yêu cầu. Vui lòng thử lại.";
        };
    }
}
