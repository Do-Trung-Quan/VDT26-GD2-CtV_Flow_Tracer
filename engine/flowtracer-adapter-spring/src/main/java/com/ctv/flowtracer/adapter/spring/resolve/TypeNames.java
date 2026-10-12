package com.ctv.flowtracer.adapter.spring.resolve;

import java.util.ArrayList;
import java.util.List;

/** Xử lý chuỗi tên kiểu viết trong mã nguồn ({@code Map<String, List<Foo>>}, {@code a.b.Foo[]}). */
final class TypeNames {

    private TypeNames() {}

    /** Bỏ phần generic và dấu mảng/varargs: {@code List<Foo>[]} → {@code List}. */
    static String stripGenerics(String type) {
        int generic = type.indexOf('<');
        String raw = generic < 0 ? type : type.substring(0, generic);
        return raw.replace("[]", "").replace("...", "").strip();
    }

    /** Phần sau dấu chấm cuối: {@code a.b.Foo} → {@code Foo}. */
    static String simple(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    /**
     * Các đối số generic cấp ngoài cùng: {@code JpaRepository<Category, Long>} → {@code [Category, Long]};
     * {@code Map<String, List<Foo>>} → {@code [String, List<Foo>]}. Rỗng nếu không có generic.
     */
    static List<String> typeArguments(String type) {
        int open = type.indexOf('<');
        int close = type.lastIndexOf('>');
        if (open < 0 || close < open) {
            return List.of();
        }
        var result = new ArrayList<String>();
        int depth = 0;
        int start = open + 1;
        for (int i = start; i < close; i++) {
            char c = type.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                result.add(type.substring(start, i).strip());
                start = i + 1;
            }
        }
        result.add(type.substring(start, close).strip());
        return result;
    }
}
