package com.ctv.flowtracer.adapter.spring.resolve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Đọc văn bản JPQL / SQL: loại câu lệnh và các tên được tham chiếu sau {@code FROM}, {@code JOIN}, {@code UPDATE},
 * {@code INTO}. Không phải bộ phân tích cú pháp đầy đủ: bỏ qua chú thích và chuỗi literal, hiểu danh sách bảng cách nhau
 * bằng dấu phẩy, và bỏ truy vấn con. Câu phức tạp hơn thì thiếu bảng chứ không bịa bảng.
 */
public final class QueryTextAnalyzer {

    /** Một tên entity/bảng; {@code joined = true} nếu đứng sau JOIN (trong JPQL có thể là đường dẫn liên kết). */
    public record Identifier(String name, boolean joined) {}

    /**
     * @param verb {@code SELECT}, {@code SELECT COUNT(*)}, {@code INSERT}, {@code UPDATE}, {@code DELETE},
     *     {@code INSERT/UPDATE}; null nếu không nhận ra
     * @param operation {@code READ}, {@code WRITE} hoặc {@code DELETE}; null nếu không nhận ra
     */
    public record Analysis(String verb, String operation, List<Identifier> identifiers) {}

    private static final Pattern COMMENTS = Pattern.compile("--[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    private static final Pattern VERB = Pattern.compile("(?i)\\b(select|insert|update|delete|merge)\\b");
    private static final Pattern COUNT = Pattern.compile("(?i)^select\\s+(?:distinct\\s+)?count\\s*\\(");
    private static final Pattern FROM = Pattern.compile("(?i)\\bfrom\\b");
    private static final Pattern JOIN = Pattern.compile("(?i)\\bjoin\\s+(?:fetch\\s+)?([\"`\\[]?[A-Za-z_][\\w.$\"`\\]]*)");
    private static final Pattern UPDATE_OR_INTO =
            Pattern.compile("(?i)\\b(?:update|into)\\s+([\"`\\[]?[A-Za-z_][\\w.$\"`\\]]*)");
    private static final Pattern CLAUSE_END =
            Pattern.compile("(?i)(?:where|group|order|having|union|limit|fetch|offset|for|on|set|values|select|inner|left|right|full|cross|join)\\b");

    /** Tên kèm vị trí trong văn bản, để giữ thứ tự xuất hiện. */
    private record Found(int position, Identifier identifier) {}

    public Analysis analyze(String queryText) {
        String text = STRING_LITERAL
                .matcher(COMMENTS.matcher(queryText).replaceAll(" "))
                .replaceAll("''")
                .replaceAll("\\s+", " ")
                .strip();
        String verb = null;
        String operation = null;
        Matcher v = VERB.matcher(text);
        if (v.find()) {
            switch (v.group(1).toLowerCase(Locale.ROOT)) {
                case "select" -> {
                    verb = COUNT.matcher(text.substring(v.start())).find() ? "SELECT COUNT(*)" : "SELECT";
                    operation = "READ";
                }
                case "insert" -> {
                    verb = "INSERT";
                    operation = "WRITE";
                }
                case "update" -> {
                    verb = "UPDATE";
                    operation = "WRITE";
                }
                case "merge" -> {
                    verb = "INSERT/UPDATE";
                    operation = "WRITE";
                }
                default -> {
                    verb = "DELETE";
                    operation = "DELETE";
                }
            }
        }
        var found = new ArrayList<Found>();
        Matcher f = FROM.matcher(text);
        while (f.find()) {
            addFromClause(text, f.end(), found);
        }
        collect(JOIN, text, true, found);
        collect(UPDATE_OR_INTO, text, false, found);
        found.sort(Comparator.comparingInt(Found::position));
        var unique = new LinkedHashMap<String, Identifier>();
        found.forEach(x -> unique.putIfAbsent(x.identifier().name().toLowerCase(Locale.ROOT) + x.identifier().joined(), x.identifier()));
        return new Analysis(verb, operation, new ArrayList<>(unique.values()));
    }

    /** Các tên (không phân biệt có đứng sau JOIN hay không), theo thứ tự xuất hiện, không trùng. */
    public List<String> referencedTables(String queryText) {
        return analyze(queryText).identifiers().stream().map(Identifier::name).distinct().toList();
    }

    private static void collect(Pattern pattern, String text, boolean joined, List<Found> out) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            out.add(new Found(m.start(1), new Identifier(clean(m.group(1)), joined)));
        }
    }

    /** Mệnh đề sau FROM: các phần cách nhau bằng dấu phẩy ở mức ngoài cùng; mỗi phần lấy từ đầu tiên (bỏ truy vấn con). */
    private static void addFromClause(String text, int start, List<Found> out) {
        Matcher keyword = CLAUSE_END.matcher(text);
        int depth = 0;
        int partStart = start;
        int i = start;
        while (i <= text.length()) {
            boolean end = i == text.length();
            if (!end) {
                char c = text.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    if (depth == 0) {
                        end = true;
                    } else {
                        depth--;
                    }
                } else if (depth == 0 && Character.isLetter(c) && (i == start || startsWord(text, i))) {
                    keyword.region(i, text.length());
                    end = keyword.lookingAt();
                }
            }
            if (end || (i < text.length() && text.charAt(i) == ',' && depth == 0)) {
                addPart(text, partStart, i, out);
                partStart = i + 1;
            }
            if (end) {
                return;
            }
            i++;
        }
    }

    /** Vị trí {@code i} bắt đầu một từ riêng (không nằm giữa tên có dấu chấm hay trong tên đặt trong dấu nháy). */
    private static boolean startsWord(String text, int i) {
        char previous = text.charAt(i - 1);
        return !Character.isLetterOrDigit(previous) && "_.\"`[".indexOf(previous) < 0;
    }

    private static void addPart(String text, int from, int to, List<Found> out) {
        String part = text.substring(from, to);
        String trimmed = part.stripLeading();
        if (trimmed.isEmpty() || trimmed.charAt(0) == '(') {
            return;
        }
        String first = trimmed.split("[\\s(]", 2)[0];
        if (!first.isEmpty()) {
            out.add(new Found(from + (part.length() - trimmed.length()), new Identifier(clean(first), false)));
        }
    }

    private static String clean(String identifier) {
        return identifier.replaceAll("[\"`\\[\\]]", "");
    }

    /** Dùng cho test: tên kèm cờ JOIN theo thứ tự xuất hiện. */
    static Map<String, Boolean> asMap(Analysis analysis) {
        var map = new LinkedHashMap<String, Boolean>();
        analysis.identifiers().forEach(i -> map.put(i.name(), i.joined()));
        return map;
    }
}
