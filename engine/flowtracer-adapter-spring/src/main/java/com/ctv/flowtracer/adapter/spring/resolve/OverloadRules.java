package com.ctv.flowtracer.adapter.spring.resolve;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chọn hàm nạp chồng (cùng tên, cùng số tham số) theo KIỂU của đối số, như trình biên dịch Java làm. Chỉ loại một ứng viên khi CHẮC
 * CHẮN không dùng được: kiểu đối số đã biết, kiểu tham số thuộc một nhóm kiểu quen thuộc (chuỗi, số, logic, tập hợp, map, ngày) và
 * hai nhóm khác nhau (vd đối số {@code String} không truyền được vào tham số {@code Collection<String>}). Mọi trường hợp còn lại —
 * kiểu chưa biết, kiểu của dự án, quan hệ kế thừa — giữ ứng viên, nên không bao giờ loại nhầm; còn nhiều ứng viên thì vẫn là
 * {@code UNRESOLVED} như trước.
 */
final class OverloadRules {

    private static final Set<String> NUMBERS = Set.of(
            "int", "long", "short", "byte", "float", "double", "integer", "number", "bigdecimal", "biginteger");
    private static final Set<String> STRINGS = Set.of("string", "charsequence", "char", "character");
    private static final Set<String> BOOLEANS = Set.of("boolean");
    private static final Set<String> COLLECTIONS = Set.of(
            "collection", "list", "set", "arraylist", "linkedlist", "hashset", "treeset", "linkedhashset", "iterable", "queue", "deque",
            "sortedset");
    private static final Set<String> MAPS = Set.of("map", "hashmap", "treemap", "linkedhashmap", "sortedmap");
    private static final Set<String> DATES = Set.of(
            "date", "localdate", "localdatetime", "instant", "zoneddatetime", "offsetdatetime", "timestamp", "calendar");

    private OverloadRules() {}

    /**
     * Ứng viên có dùng được với đối số này không (false chỉ khi chắc chắn không).
     *
     * @param argType kiểu đối số (tên ngắn); null nếu chưa biết
     * @param paramType kiểu tham số (có thể kèm generic, mảng)
     */
    static boolean mayAccept(String argType, String paramType) {
        if (argType == null || paramType == null) {
            return true;
        }
        String arg = base(argType);
        String param = base(paramType);
        if (arg.equals(param) || param.equals("object") || param.length() == 1) {
            return true; // trùng kiểu, tham số Object, hoặc biến kiểu generic (T, E…)
        }
        if (paramType.endsWith("[]") != argType.endsWith("[]")) {
            return !(group(arg) != null && group(param) != null); // mảng với không phải mảng: chỉ loại khi cả hai là kiểu quen thuộc
        }
        String a = group(arg);
        String p = group(param);
        return a == null || p == null || a.equals(p);
    }

    /** Các ứng viên còn lại sau khi loại những hàm chắc chắn không nhận được các đối số này. */
    static <T> List<T> narrow(List<T> candidates, List<List<String>> paramTypesOf, List<String> argTypes) {
        if (argTypes == null || argTypes.isEmpty()) {
            return candidates;
        }
        var kept = new java.util.ArrayList<T>();
        for (int i = 0; i < candidates.size(); i++) {
            List<String> params = paramTypesOf.get(i);
            boolean ok = true;
            for (int k = 0; k < Math.min(params.size(), argTypes.size()); k++) {
                if (!mayAccept(argTypes.get(k), params.get(k))) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                kept.add(candidates.get(i));
            }
        }
        return kept.isEmpty() ? candidates : kept; // loại hết nghĩa là dữ kiện sai: giữ nguyên, đừng đoán
    }

    private static String base(String type) {
        String t = type.strip();
        int generic = t.indexOf('<');
        if (generic >= 0) {
            t = t.substring(0, generic);
        }
        t = t.replace("[]", "").replace("...", "");
        int dot = t.lastIndexOf('.');
        return (dot >= 0 ? t.substring(dot + 1) : t).toLowerCase(Locale.ROOT);
    }

    private static String group(String simpleLower) {
        if (STRINGS.contains(simpleLower)) {
            return "string";
        }
        if (NUMBERS.contains(simpleLower)) {
            return "number";
        }
        if (BOOLEANS.contains(simpleLower)) {
            return "boolean";
        }
        if (COLLECTIONS.contains(simpleLower)) {
            return "collection";
        }
        if (MAPS.contains(simpleLower)) {
            return "map";
        }
        return DATES.contains(simpleLower) ? "date" : null;
    }
}
