package com.ctv.flowtracer.app.ai.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Nạp prompt từ {@code resources/prompts/<tên>.st} (CLAUDE.md §3.6). File chia 3 phần bằng dòng {@code ### SYSTEM},
 * {@code ### USER}, {@code ### SCHEMA}; chỗ trống dạng {@code {{tên}}} được thay bằng dữ kiện. Tên file có phiên bản
 * ({@code purpose-text.v1}): đổi nội dung prompt thì tăng phiên bản vì phiên bản nằm trong khóa cache.
 */
@Component
public class PromptTemplates {

    /** Prompt đã điền dữ kiện. */
    public record Rendered(String version, String system, String user, String schema) {}

    private static final String[] KEYS = {"### SYSTEM", "### USER", "### SCHEMA"};

    private final Map<String, String[]> cache = new ConcurrentHashMap<>();

    public Rendered render(String name, Map<String, String> values) {
        String[] parts = cache.computeIfAbsent(name, this::load);
        return new Rendered(name, fill(parts[0], values), fill(parts[1], values), parts[2].strip());
    }

    private String[] load(String name) {
        String path = "prompts/" + name + ".st";
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Không có prompt " + path);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            String[] sections = new String[KEYS.length];
            for (int i = 0; i < KEYS.length; i++) {
                int from = text.indexOf(KEYS[i]);
                if (from < 0) {
                    throw new IllegalStateException("Prompt " + path + " thiếu phần " + KEYS[i]);
                }
                int start = from + KEYS[i].length();
                int end = text.length();
                for (String other : KEYS) {
                    int at = text.indexOf(other, start);
                    if (at >= 0 && at < end) {
                        end = at;
                    }
                }
                sections[i] = text.substring(start, end).strip();
            }
            return sections;
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được prompt " + path, e);
        }
    }

    private static String fill(String template, Map<String, String> values) {
        String result = template;
        for (var e : values.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return result;
    }
}
