package com.ctv.flowtracer.adapter.spring.library;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bảng import của một file rút gọn, và bộ đổi chữ ký kiểu của file {@code .class} ({@code Ljava/util/List<Lcom/x/Foo;>;},
 * {@code TT;}) sang cú pháp Java. Tên đơn giản được dùng khi không trùng; trùng thì dùng tên đầy đủ.
 */
final class StubTypes {

    private static final Set<String> JAVA_LANG_SKIP_IMPORT = Set.of("java.lang");

    private final Map<String, String> importBySimple = new TreeMap<>();
    private final Set<String> referenced = new LinkedHashSet<>();

    /** Mọi lớp (tên đầy đủ, dạng {@code com.x.Foo}) mà chữ ký đã nhắc tới, theo thứ tự gặp. */
    Set<String> referenced() {
        return referenced;
    }

    List<String> imports() {
        return importBySimple.values().stream()
                .filter(fqn -> !JAVA_LANG_SKIP_IMPORT.contains(packageOf(fqn)))
                .sorted()
                .toList();
    }

    /** Tên dùng trong mã cho lớp {@code com.x.Outer$Inner}: {@code Outer.Inner}, kèm import của lớp ngoài. */
    String ref(String internalOrDotted) {
        String fqn = internalOrDotted.replace('/', '.');
        int nested = fqn.indexOf('$');
        String outer = nested < 0 ? fqn : fqn.substring(0, nested);
        String suffix = nested < 0 ? "" : fqn.substring(nested).replace('$', '.');
        referenced.add(outer);
        String simple = outer.substring(outer.lastIndexOf('.') + 1);
        String existing = importBySimple.putIfAbsent(simple, outer);
        if (existing != null && !existing.equals(outer)) {
            return outer + suffix; // trùng tên đơn giản với một lớp khác đã import
        }
        return simple + suffix;
    }

    private static String packageOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? "" : fqn.substring(0, dot);
    }

    // ---------------- chữ ký kiểu ----------------

    /** Mô tả trường ({@code Ljava/lang/String;}) hoặc chữ ký generic ({@code Ljava/util/List<TT;>;}) → kiểu Java. */
    String type(String signature) {
        return new Cursor(signature).type();
    }

    /** Chữ ký lớp generic: tên các tham số kiểu ({@code <T:Ljava/lang/Object;>…} → {@code [T]}) và kiểu lớp cha. */
    ClassSignature classSignature(String signature) {
        return new Cursor(signature).classSignature();
    }

    record ClassSignature(List<String> typeParameters, String superType) {}

    private final class Cursor {
        private final String s;
        private int i;

        Cursor(String s) {
            this.s = s;
        }

        ClassSignature classSignature() {
            var params = new ArrayList<String>();
            if (peek() == '<') {
                i++;
                while (peek() != '>') {
                    int colon = s.indexOf(':', i);
                    params.add(s.substring(i, colon));
                    i = colon;
                    while (peek() == ':') { // ràng buộc lớp / interface (có thể rỗng)
                        i++;
                        char next = peek();
                        if (next == 'L' || next == 'T' || next == '[') {
                            type();
                        }
                    }
                }
                i++;
            }
            String superType = i < s.length() ? type() : null;
            return new ClassSignature(List.copyOf(params), superType);
        }

        String type() {
            char c = s.charAt(i++);
            switch (c) {
                case 'B': return "byte";
                case 'C': return "char";
                case 'D': return "double";
                case 'F': return "float";
                case 'I': return "int";
                case 'J': return "long";
                case 'S': return "short";
                case 'Z': return "boolean";
                case 'V': return "void";
                case '[': return type() + "[]";
                case 'T': {
                    int end = s.indexOf(';', i);
                    String name = s.substring(i, end);
                    i = end + 1;
                    return name;
                }
                case 'L': return classType();
                default: throw new IllegalArgumentException("Chữ ký kiểu sai: " + s);
            }
        }

        private String classType() {
            var name = new StringBuilder();
            while (true) {
                char c = s.charAt(i);
                if (c == '<' || c == ';' || c == '.') {
                    break;
                }
                name.append(c);
                i++;
            }
            var out = new StringBuilder(ref(name.toString()));
            while (true) {
                char c = s.charAt(i);
                if (c == '<') {
                    out.append(typeArguments());
                } else if (c == '.') { // lớp lồng của lớp generic: Outer<T>.Inner
                    i++;
                    var inner = new StringBuilder();
                    while (s.charAt(i) != '<' && s.charAt(i) != ';' && s.charAt(i) != '.') {
                        inner.append(s.charAt(i++));
                    }
                    out.append('.').append(inner);
                } else {
                    i++; // ';'
                    return out.toString();
                }
            }
        }

        private String typeArguments() {
            i++; // '<'
            var args = new ArrayList<String>();
            while (s.charAt(i) != '>') {
                char c = s.charAt(i);
                if (c == '*') {
                    i++;
                    args.add("?");
                } else if (c == '+') {
                    i++;
                    args.add("? extends " + type());
                } else if (c == '-') {
                    i++;
                    args.add("? super " + type());
                } else {
                    args.add(type());
                }
            }
            i++; // '>'
            return "<" + String.join(", ", args) + ">";
        }

        private char peek() {
            return i < s.length() ? s.charAt(i) : '\0';
        }
    }
}
