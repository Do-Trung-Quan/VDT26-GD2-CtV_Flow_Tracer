package com.ctv.flowtracer.app.linking.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tín hiệu so khớp TÊN, thuần và tất định: những từ trong chuỗi gọi (tên hàm, chuỗi hằng truyền vào) trùng với từ trong đường dẫn /
 * tên của từng ứng viên — vd {@code loadMostBorrowed → getMostBorrowedBooks('most-borrowed-books')} khớp
 * {@code GET …/most-borrowed-books} ở các từ "most", "borrowed", "books". Chỉ giữ từ PHÂN BIỆT được các ứng viên (từ có ở mọi
 * ứng viên như "api", "public", tên dự án… không nói lên gì). Chỉ để HIỂN THỊ và xếp thứ tự gợi ý; không đổi liên kết hay mức tin cậy.
 */
final class CandidateHintRule {

    /** Từ quá chung trong mã nguồn / đường dẫn, không dùng làm tín hiệu. */
    private static final Set<String> STOP = Set.of(
            "get", "set", "post", "put", "delete", "patch", "load", "fetch", "find", "list", "all", "data", "item", "items", "api", "public",
            "private", "http", "https", "www", "com", "org", "net", "the", "for", "and", "with", "from", "into", "service", "facade",
            "repository", "repo", "component", "controller", "impl", "base", "url", "request", "response", "handle", "click", "submit",
            "init", "this", "new", "await", "async", "return", "const", "var", "function", "void", "string", "number", "boolean", "promise",
            "observable", "args", "param", "params", "path", "ids", "key", "value", "result", "res", "req", "err", "error", "status",
            "reference", "resource");

    private CandidateHintRule() {}

    /** Hạ chữ thường và bỏ dấu để so khớp. */
    static String fold(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
    }

    /** Các từ có nghĩa của một đoạn chữ: tách camelCase / dấu gạch / dấu chấm, bỏ từ ngắn, số và từ quá chung. */
    static List<String> words(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        String spaced = text.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        for (String raw : fold(spaced).split("[^a-z0-9]+")) {
            if (raw.length() >= 3 && !raw.chars().allMatch(Character::isDigit) && !STOP.contains(raw)) {
                result.add(raw);
            }
        }
        return result;
    }

    /** Gốc từ đơn giản để "books" ~ "book", "borrowed" ~ "borrow". */
    static String stem(String word) {
        String w = word;
        if (w.length() > 5 && w.endsWith("ing")) {
            w = w.substring(0, w.length() - 3);
        } else if (w.length() > 4 && w.endsWith("ed")) {
            w = w.substring(0, w.length() - 2);
        } else if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) {
            w = w.substring(0, w.length() - 1);
        }
        return w;
    }

    /**
     * @param contextWords từ trong chuỗi gọi (tên hàm, tên phần tử, chuỗi hằng truyền vào)
     * @param candidates khóa ứng viên → chữ mô tả ứng viên (đường dẫn / tên API)
     * @return khóa ứng viên → các từ (dạng viết ở ứng viên) khớp với chuỗi gọi và phân biệt được ứng viên; rỗng nếu không có
     */
    static Map<String, List<String>> hints(Iterable<String> contextWords, Map<String, String> candidates) {
        Set<String> context = new HashSet<>();
        contextWords.forEach(w -> context.add(stem(w)));
        Map<String, Map<String, String>> byCandidate = new LinkedHashMap<>();
        candidates.forEach((key, text) -> {
            Map<String, String> stems = new LinkedHashMap<>();
            words(text).forEach(w -> stems.putIfAbsent(stem(w), w));
            byCandidate.put(key, stems);
        });
        Set<String> common = new LinkedHashSet<>();
        if (byCandidate.size() > 1) {
            common.addAll(byCandidate.values().iterator().next().keySet());
            byCandidate.values().forEach(m -> common.retainAll(m.keySet()));
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        byCandidate.forEach((key, stems) -> {
            List<String> matched = new ArrayList<>();
            stems.forEach((stem, surface) -> {
                if (context.contains(stem) && !common.contains(stem)) {
                    matched.add(surface);
                }
            });
            result.put(key, matched);
        });
        return result;
    }
}
