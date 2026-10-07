package com.ctv.flowtracer.ir;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Quy tắc tạo/đọc node key ổn định. Dạng chung: {@code <namespace>:<loại>:<giá trị>}.
 *
 * <p><b>ĐỔI QUY TẮC = BREAKING CHANGE</b>: liên kết chốt tay, nhóm chức năng và cache AI đều trỏ tới node bằng
 * key. Bản tương ứng phía sidecar nằm ở {@code analyzer-node/src/protocol/node-keys.ts}.
 */
public final class NodeKeys {

    public static final String JAVA = "java";
    public static final String ANGULAR = "ng";
    public static final String PHP = "php";

    private NodeKeys() {}

    /** {@code java:class:<FQN>} */
    public static String javaClass(String fqn) {
        return join(JAVA, "class", fqn);
    }

    /** {@code java:method:<FQN>#<tên>(<kiểu tham số, cách nhau bởi dấu phẩy>)} */
    public static String javaMethod(String declaringFqn, String methodName, List<String> paramTypes) {
        return join(JAVA, "method", declaringFqn + "#" + methodName + "(" + String.join(",", paramTypes) + ")");
    }

    /** {@code java:endpoint:<HTTP_METHOD> <path>} — path đã chuẩn hóa, biến đường dẫn giữ dạng {tên}. */
    public static String javaEndpoint(String httpMethod, String path) {
        return join(JAVA, "endpoint", httpMethod.toUpperCase(Locale.ROOT) + " " + path);
    }

    /** {@code java:query:<method key>:<thứ tự>} */
    public static String javaQuery(String methodKey, int index) {
        return join(JAVA, "query", methodKey + ":" + index);
    }

    /** {@code java:table:<tên bảng>} — tên bảng viết thường. */
    public static String javaTable(String tableName) {
        return join(JAVA, "table", tableName.toLowerCase(Locale.ROOT));
    }

    /** {@code java:type:<FQN>} */
    public static String javaType(String fqn) {
        return join(JAVA, "type", fqn);
    }

    /** {@code ng:element:<app>/<component>#<id>} */
    public static String ngElement(String app, String component, String elementId) {
        return join(ANGULAR, "element", app + "/" + component + "#" + elementId);
    }

    /** {@code ng:http:<app>/<class>#<method>:<thứ tự>} */
    public static String ngHttpCall(String app, String className, String methodName, int index) {
        return join(ANGULAR, "http", app + "/" + className + "#" + methodName + ":" + index);
    }

    /** Tách key thành 3 phần; ném lỗi nếu sai định dạng. */
    public static Parts parse(String key) {
        Objects.requireNonNull(key, "key");
        int first = key.indexOf(':');
        int second = first < 0 ? -1 : key.indexOf(':', first + 1);
        if (first <= 0 || second <= first + 1 || second == key.length() - 1) {
            throw new IllegalArgumentException("Node key sai định dạng: " + key);
        }
        return new Parts(key.substring(0, first), key.substring(first + 1, second), key.substring(second + 1));
    }

    private static String join(String namespace, String type, String value) {
        Objects.requireNonNull(value, "value");
        return namespace + ":" + type + ":" + value;
    }

    /** Các phần của một node key. */
    public record Parts(String namespace, String type, String value) {}
}
