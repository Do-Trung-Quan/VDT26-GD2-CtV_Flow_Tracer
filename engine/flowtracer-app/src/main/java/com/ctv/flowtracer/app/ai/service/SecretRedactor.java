package com.ctv.flowtracer.app.ai.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Che secret trong mã nguồn TRƯỚC khi gửi tới AI bên ngoài (CLAUDE.md §3.4, NFR-13): giá trị của khóa có tên chứa
 * {@code password / secret / token / key / credential}, chuỗi kết nối CSDL và dịch vụ, khối private key, token dạng
 * {@code Bearer …} và vài dạng API key quen thuộc. Phần tên khóa được giữ để AI vẫn hiểu cấu trúc. Quy tắc thuần, test được.
 */
public final class SecretRedactor {

    static final String MASK = "***";

    private static final Pattern PRIVATE_KEY =
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----", Pattern.DOTALL);

    /** Tên khóa nhạy cảm: "key" chỉ tính khi đứng riêng hoặc là hậu tố (apiKey, secret_key), không tính "keyword". */
    private static final String SENSITIVE_NAME =
            "[\\w.\\-]*(?:password|passwd|pwd|secret|token|credential|(?<![A-Za-z])key(?![a-z])|[a-z]Key(?![a-z])|[_.\\-]key(?![a-z]))[\\w.\\-]*";

    /** {@code name = "value"}, {@code name: value}, {@code "name": "value"}. */
    private static final Pattern KEY_VALUE = Pattern.compile(
            "(?i)(" + SENSITIVE_NAME + "[\"']?\\s*[:=]\\s*)(\"[^\"\\n]*\"|'[^'\\n]*'|[^\\s,;\"'}\\])]+)");

    private static final Pattern CONNECTION_URL = Pattern.compile(
            "(?i)\\b((?:jdbc:[a-z0-9]+|mongodb(?:\\+srv)?|redis|rediss|amqp|amqps|postgres(?:ql)?|mysql|mariadb|sqlserver|oracle)):[^\\s\"'<>`]+");

    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+/\\-]{16,}=*");

    private static final List<Pattern> KNOWN_TOKENS = List.of(
            Pattern.compile("AIza[0-9A-Za-z_\\-]{30,}"),
            Pattern.compile("\\bsk-[A-Za-z0-9_\\-]{20,}"),
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{30,}"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"));

    /** Tên file không bao giờ được gửi tới AI (CLAUDE.md §3.4): file môi trường, keystore, khóa, dữ liệu. */
    private static final Pattern FORBIDDEN_FILE = Pattern.compile(
            "(?i)(^|/)(\\.env(\\..*)?|[^/]*\\.(jks|keystore|p12|pfx|pem|key|crt|sqlite|db|sql|csv|dump|bak))$");

    /** File này có được phép gửi nội dung tới AI không. */
    public static boolean isSendable(String relativePath) {
        return relativePath != null && !FORBIDDEN_FILE.matcher(relativePath).find();
    }

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = PRIVATE_KEY.matcher(text).replaceAll(MASK);
        result = CONNECTION_URL.matcher(result).replaceAll("$1://" + MASK);
        result = replaceValues(result);
        result = BEARER.matcher(result).replaceAll("$1" + MASK);
        for (Pattern known : KNOWN_TOKENS) {
            result = known.matcher(result).replaceAll(MASK);
        }
        return result;
    }

    /** Giữ tên khóa và dấu phân cách, thay giá trị; giữ nguyên cặp nháy bao quanh. */
    private static String replaceValues(String text) {
        Matcher m = KEY_VALUE.matcher(text);
        var out = new StringBuilder();
        while (m.find()) {
            String value = m.group(2);
            String quote = value.startsWith("\"") ? "\"" : value.startsWith("'") ? "'" : "";
            m.appendReplacement(out, Matcher.quoteReplacement(m.group(1) + quote + MASK + quote));
        }
        m.appendTail(out);
        return out.toString();
    }
}
