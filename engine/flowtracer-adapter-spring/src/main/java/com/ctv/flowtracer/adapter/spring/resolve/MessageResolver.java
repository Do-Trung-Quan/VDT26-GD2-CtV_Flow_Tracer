package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.io.IOException;
import java.io.StringReader;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Khóa thông báo trong {@code throw} → câu hiển thị (file {@code messages*.properties} của dự án phân tích, kể cả dạng {@code messages-app_vi.properties}). Ưu tiên bản tiếng
 * Việt ({@code messages_vi.properties}), rồi bản mặc định ({@code messages.properties}); khóa có ở nhiều file thì lấy file ưu
 * tiên cao hơn, cùng mức thì theo thứ tự đường dẫn. Chỉ đọc file thông báo, không đọc cấu hình ứng dụng; không giữ giá trị
 * nào của khóa có dạng bí mật ({@code password}, {@code secret}, {@code token}, {@code credential}).
 */
public final class MessageResolver {

    private static final Pattern MESSAGE_FILE = Pattern.compile("(?:.*/)?messages(?:-[A-Za-z0-9]+)*(?:_([a-zA-Z]{2,3})(?:_[A-Za-z]{2})?)?\\.properties");
    private static final Pattern SECRET_KEY = Pattern.compile("(?i)password|secret|token|credential");
    /** Giới hạn số khóa giữ lại để file thông báo khổng lồ không làm phình IR. */
    static final int MAX_MESSAGES = 20_000;

    public Map<String, String> loadMessages(ResolveContext ctx) {
        List<String> files = ctx.allPaths().stream()
                .filter(p -> !p.startsWith("target/") && !p.contains("/target/") && MESSAGE_FILE.matcher(p).matches() && priority(p) <= 1)
                .sorted(Comparator.comparingInt(MessageResolver::priority).thenComparing(Comparator.naturalOrder()))
                .toList();
        Map<String, String> messages = new TreeMap<>();
        for (String path : files) {
            Optional<String> content = ctx.loader().load(path);
            if (content.isEmpty()) {
                continue;
            }
            Properties props = new Properties();
            try {
                props.load(new StringReader(content.get()));
            } catch (IOException | IllegalArgumentException e) {
                continue; // file lỗi định dạng: bỏ qua, không làm hỏng lần quét
            }
            for (String key : props.stringPropertyNames()) {
                if (!SECRET_KEY.matcher(key).find() && messages.size() < MAX_MESSAGES) {
                    messages.putIfAbsent(key, props.getProperty(key).strip());
                }
            }
        }
        return messages;
    }

    /** 0 = tiếng Việt, 1 = mặc định (không mã ngôn ngữ), 2 = ngôn ngữ khác (bị loại: câu hiển thị phải là tiếng Việt hoặc mặc định). */
    private static int priority(String path) {
        var m = MESSAGE_FILE.matcher(path);
        if (!m.matches() || m.group(1) == null) {
            return 1;
        }
        return m.group(1).equalsIgnoreCase("vi") ? 0 : 2;
    }
}
