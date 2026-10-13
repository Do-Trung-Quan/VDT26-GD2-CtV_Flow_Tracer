package com.ctv.flowtracer.core.explain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Điều kiện trong code → câu dễ đọc cho mục "Ràng buộc": {@code status == null || (status != 0 && status != 1)} thành
 * "status để trống hoặc (status khác 0 và status khác 1)". Chỉ dịch các dạng đơn giản, chắc chắn đúng (so sánh với null / số /
 * chuỗi, rỗng, độ dài); đoạn nào không nhận ra thì giữ nguyên. Không nhận ra đoạn nào thì trả về nguyên điều kiện.
 */
public final class ConditionTextRule {

    private static final Pattern IS_NULL = Pattern.compile("^(.+?)\\s*==\\s*null$");
    private static final Pattern NOT_NULL = Pattern.compile("^(.+?)\\s*!=\\s*null$");
    private static final Pattern EMPTY = Pattern.compile("^(!?)\\s*(.+?)\\.(?:isEmpty|isBlank)\\(\\)$");
    private static final Pattern LENGTH_OVER = Pattern.compile("^(.+?)\\.(?:length|size)\\(\\)\\s*>\\s*([\\w.]+…?)$");
    private static final Pattern COMPARE = Pattern.compile("^([\\w.()\\[\\]]+?)\\s*(>=|<=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)$");
    private static final Pattern EQUALS = Pattern.compile("^([\\w.()\\[\\]]+?)\\s*(==|!=)\\s*(-?\\d+(?:\\.\\d+)?|\"[^\"]*\")$");

    /** @return điều kiện đã diễn đạt lại; nguyên văn nếu không nhận ra đoạn nào */
    public String describe(String condition) {
        if (condition == null || condition.isBlank()) {
            return condition;
        }
        List<String> parts = tokens(condition.strip());
        boolean changed = false;
        var out = new StringBuilder();
        for (String part : parts) {
            String converted = isDelimiter(part) ? delimiter(part) : atom(part);
            if (!isDelimiter(part) && !converted.equals(part.strip())) {
                changed = true;
            }
            out.append(converted);
        }
        return changed ? out.toString().replaceAll("\\s+", " ").strip() : condition;
    }

    // ---------------- tách thành đoạn ----------------

    /** Tách theo {@code ||}, {@code &&} và ngoặc nhóm; ngoặc của lời gọi hàm nằm trong đoạn. */
    private static List<String> tokens(String s) {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (s.startsWith("||", i) || s.startsWith("&&", i)) {
                flush(result, current);
                result.add(s.substring(i, i + 2));
                i += 2;
            } else if (c == '(' && isGrouping(current)) {
                flush(result, current);
                result.add("(");
                i++;
            } else if (c == '(') { // lời gọi hàm: lấy tới ngoặc đóng tương ứng
                int end = matching(s, i);
                current.append(s, i, end + 1);
                i = end + 1;
            } else if (c == ')') {
                flush(result, current);
                result.add(")");
                i++;
            } else {
                current.append(c);
                i++;
            }
        }
        flush(result, current);
        return result;
    }

    /** Ngoặc mở là ngoặc nhóm khi đứng sau toán tử / đầu câu / dấu {@code !} (không dính liền tên hàm). */
    private static boolean isGrouping(StringBuilder current) {
        String before = current.toString().stripTrailing();
        return before.isEmpty() || before.endsWith("!") && before.length() == 1;
    }

    private static int matching(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '(') {
                depth++;
            } else if (s.charAt(i) == ')' && --depth == 0) {
                return i;
            }
        }
        return s.length() - 1;
    }

    private static void flush(List<String> result, StringBuilder current) {
        if (!current.toString().isBlank()) {
            result.add(current.toString().strip());
        }
        current.setLength(0);
    }

    private static boolean isDelimiter(String part) {
        return part.equals("||") || part.equals("&&") || part.equals("(") || part.equals(")");
    }

    private static String delimiter(String part) {
        return switch (part) {
            case "||" -> " hoặc ";
            case "&&" -> " và ";
            default -> part;
        };
    }

    // ---------------- từng đoạn ----------------

    private static String atom(String raw) {
        String text = raw.strip().replaceFirst("^this\\.", "");
        Matcher m;
        if ((m = IS_NULL.matcher(text)).matches()) {
            return clean(m.group(1)) + " để trống";
        }
        if ((m = NOT_NULL.matcher(text)).matches()) {
            return clean(m.group(1)) + " có giá trị";
        }
        if ((m = EMPTY.matcher(text)).matches()) {
            return clean(m.group(2)) + (m.group(1).isEmpty() ? " để trống" : " có nội dung");
        }
        if ((m = LENGTH_OVER.matcher(text)).matches()) {
            String limit = m.group(2);
            String subject = clean(m.group(1));
            return limit.matches("\\d+") ? subject + " dài quá " + limit + " ký tự" : subject + " dài quá giới hạn cho phép";
        }
        if ((m = COMPARE.matcher(text)).matches()) {
            String relation = switch (m.group(2)) {
                case ">" -> "lớn hơn";
                case ">=" -> "từ";
                case "<" -> "nhỏ hơn";
                default -> "tối đa";
            };
            return clean(m.group(1)) + " " + relation + " " + m.group(3);
        }
        if ((m = EQUALS.matcher(text)).matches()) {
            return clean(m.group(1)) + (m.group(2).equals("==") ? " bằng " : " khác ") + m.group(3);
        }
        return raw.strip();
    }

    private static String clean(String subject) {
        return subject.strip().replaceFirst("^this\\.", "");
    }
}
