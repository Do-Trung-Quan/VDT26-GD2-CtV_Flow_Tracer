package com.ctv.flowtracer.adapter.spring.library;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc khai báo trong một file {@code .class} (JVMS §4): tên lớp, lớp cha, chữ ký generic, các trường kèm kiểu và annotation
 * thời gian chạy. KHÔNG nạp lớp, không chạy mã — chỉ phân tích bytes. File sai định dạng thì ném {@link IOException}.
 */
final class ClassFileReader {

    static final int ACC_STATIC = 0x0008;
    static final int ACC_INTERFACE = 0x0200;
    static final int ACC_ABSTRACT = 0x0400;
    static final int ACC_SYNTHETIC = 0x1000;
    static final int ACC_ANNOTATION = 0x2000;
    static final int ACC_ENUM = 0x4000;

    /** Một annotation: kiểu ({@code Lcom/x/Foo;}) và các phần tử đã viết lại thành mã Java (tên → giá trị). */
    record Annotation(String descriptor, Map<String, String> elements) {}

    record Field(int access, String name, String descriptor, String signature, List<Annotation> annotations) {}

    /** Một hàm: chỉ cần tên và cờ truy cập để biết lớp có hành vi nghiệp vụ hay chỉ chứa dữ liệu. */
    record Method(int access, String name) {}

    /** @param internalName dạng {@code com/acme/Foo}; {@code signature} null nếu lớp không generic */
    record ClassInfo(int access, String internalName, String superName, String signature, List<Field> fields, List<Method> methods) {}

    private static final int MAX_FIELDS = 300;
    private static final int MAX_ELEMENTS = 64;

    private final DataInputStream in;
    private Object[] pool;

    private ClassFileReader(byte[] bytes) {
        this.in = new DataInputStream(new ByteArrayInputStream(bytes));
    }

    static ClassInfo read(byte[] bytes) throws IOException {
        return new ClassFileReader(bytes).parse();
    }

    private ClassInfo parse() throws IOException {
        if (in.readInt() != 0xCAFEBABE) {
            throw new IOException("Không phải file .class");
        }
        in.readUnsignedShort(); // minor
        in.readUnsignedShort(); // major
        readPool();
        int access = in.readUnsignedShort();
        String name = className(in.readUnsignedShort());
        int superIndex = in.readUnsignedShort();
        String superName = superIndex == 0 ? null : className(superIndex);
        int interfaces = in.readUnsignedShort();
        for (int i = 0; i < interfaces; i++) {
            in.readUnsignedShort();
        }
        int fieldCount = in.readUnsignedShort();
        if (fieldCount > MAX_FIELDS) {
            throw new IOException("Lớp có quá nhiều trường");
        }
        var fields = new ArrayList<Field>();
        for (int i = 0; i < fieldCount; i++) {
            fields.add(readField());
        }
        int methodCount = in.readUnsignedShort();
        var methods = new ArrayList<Method>();
        for (int i = 0; i < methodCount; i++) {
            methods.add(readMethod());
        }
        String signature = null;
        int attributes = in.readUnsignedShort();
        for (int i = 0; i < attributes; i++) {
            String attrName = utf(in.readUnsignedShort());
            int length = in.readInt();
            if (attrName.equals("Signature")) {
                signature = utf(in.readUnsignedShort());
            } else {
                skip(length);
            }
        }
        return new ClassInfo(access, name, superName, signature, List.copyOf(fields), List.copyOf(methods));
    }

    // ---------------- hằng số ----------------

    private void readPool() throws IOException {
        int count = in.readUnsignedShort();
        pool = new Object[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1 -> pool[i] = in.readUTF();
                case 3 -> pool[i] = in.readInt();
                case 4 -> pool[i] = in.readFloat();
                case 5 -> {
                    pool[i] = in.readLong();
                    i++;
                }
                case 6 -> {
                    pool[i] = in.readDouble();
                    i++;
                }
                case 7, 8, 16, 19, 20 -> pool[i] = new Ref(in.readUnsignedShort());
                case 9, 10, 11, 12, 17, 18 -> skip(4);
                case 15 -> skip(3);
                default -> throw new IOException("Hằng số không hỗ trợ: " + tag);
            }
        }
    }

    /** Hằng số trỏ tới một hằng khác (Class, String…). */
    private record Ref(int index) {}

    private String utf(int index) throws IOException {
        if (index <= 0 || index >= pool.length || !(pool[index] instanceof String s)) {
            throw new IOException("Chỉ số hằng số sai: " + index);
        }
        return s;
    }

    private String className(int index) throws IOException {
        if (index <= 0 || index >= pool.length || !(pool[index] instanceof Ref ref)) {
            throw new IOException("Chỉ số lớp sai: " + index);
        }
        return utf(ref.index());
    }

    private Object constant(int index) throws IOException {
        if (index <= 0 || index >= pool.length || pool[index] == null) {
            throw new IOException("Chỉ số hằng số sai: " + index);
        }
        return pool[index];
    }

    // ---------------- trường và annotation ----------------

    private Field readField() throws IOException {
        int access = in.readUnsignedShort();
        String name = utf(in.readUnsignedShort());
        String descriptor = utf(in.readUnsignedShort());
        String signature = null;
        var annotations = new ArrayList<Annotation>();
        int attributes = in.readUnsignedShort();
        for (int i = 0; i < attributes; i++) {
            String attrName = utf(in.readUnsignedShort());
            int length = in.readInt();
            switch (attrName) {
                case "Signature" -> signature = utf(in.readUnsignedShort());
                case "RuntimeVisibleAnnotations" -> {
                    int n = in.readUnsignedShort();
                    for (int a = 0; a < n; a++) {
                        annotations.add(readAnnotation());
                    }
                }
                default -> skip(length);
            }
        }
        return new Field(access, name, descriptor, signature, List.copyOf(annotations));
    }

    private Method readMethod() throws IOException {
        int access = in.readUnsignedShort();
        String name = utf(in.readUnsignedShort());
        in.readUnsignedShort(); // descriptor
        int attributes = in.readUnsignedShort();
        for (int i = 0; i < attributes; i++) {
            in.readUnsignedShort();
            skip(in.readInt());
        }
        return new Method(access, name);
    }

    private Annotation readAnnotation() throws IOException {
        String type = utf(in.readUnsignedShort());
        int pairs = in.readUnsignedShort();
        var elements = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs; i++) {
            String name = utf(in.readUnsignedShort());
            String value = readElementValue();
            if (value != null && elements.size() < MAX_ELEMENTS) {
                elements.put(name, value);
            }
        }
        return new Annotation(type, elements);
    }

    /** Giá trị phần tử annotation viết lại thành mã Java; null với kiểu không biểu diễn được (annotation lồng, lớp). */
    private String readElementValue() throws IOException {
        int tag = in.readUnsignedByte();
        switch (tag) {
            case 'B', 'C', 'I', 'S' -> {
                int v = ((Number) constant(in.readUnsignedShort())).intValue();
                return tag == 'C' ? "'" + (char) v + "'" : String.valueOf(v);
            }
            case 'Z' -> {
                return ((Number) constant(in.readUnsignedShort())).intValue() != 0 ? "true" : "false";
            }
            case 'J' -> {
                return ((Number) constant(in.readUnsignedShort())).longValue() + "L";
            }
            case 'F' -> {
                return ((Number) constant(in.readUnsignedShort())).floatValue() + "f";
            }
            case 'D' -> {
                return String.valueOf(((Number) constant(in.readUnsignedShort())).doubleValue());
            }
            case 's' -> {
                return quote(utf(in.readUnsignedShort()));
            }
            case 'e' -> {
                String type = utf(in.readUnsignedShort());
                String constant = utf(in.readUnsignedShort());
                return simpleName(type) + "." + constant;
            }
            case 'c' -> {
                in.readUnsignedShort();
                return null;
            }
            case '@' -> {
                readAnnotation();
                return null;
            }
            case '[' -> {
                int n = in.readUnsignedShort();
                var values = new ArrayList<String>();
                for (int i = 0; i < n; i++) {
                    String v = readElementValue();
                    if (v != null) {
                        values.add(v);
                    }
                }
                return "{" + String.join(", ", values) + "}";
            }
            default -> throw new IOException("Kiểu phần tử annotation không hỗ trợ: " + tag);
        }
    }

    private static String simpleName(String descriptor) {
        String name = descriptor.startsWith("L") && descriptor.endsWith(";") ? descriptor.substring(1, descriptor.length() - 1) : descriptor;
        return name.substring(name.lastIndexOf('/') + 1).replace('$', '.');
    }

    private static String quote(String s) {
        var out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c < 0x20 ? ' ' : c);
            }
        }
        return out.append('"').toString();
    }

    private void skip(int bytes) throws IOException {
        int left = bytes;
        while (left > 0) {
            int skipped = in.skipBytes(left);
            if (skipped <= 0) {
                throw new IOException("File .class bị cắt cụt");
            }
            left -= skipped;
        }
    }
}
