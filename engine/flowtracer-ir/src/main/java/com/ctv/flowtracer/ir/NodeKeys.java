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
    /** Node do Linker tạo (không thuộc adapter nào). */
    public static final String EXTERNAL = "ext";

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

    /**
     * {@code java:http:<FQN>#<tên>(<kiểu tham số>):<thứ tự>} — lời gọi HTTP sang service khác (RestTemplate, WebClient,
     * Feign) trong method {@code methodKey} ({@link #javaMethod}).
     */
    public static String javaHttpCall(String methodKey, int index) {
        String prefix = JAVA + ":method:";
        String method = methodKey.startsWith(prefix) ? methodKey.substring(prefix.length()) : methodKey;
        return join(JAVA, "http", method + ":" + index);
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

    /** {@code ng:app:<app>} */
    public static String ngApp(String app) {
        return join(ANGULAR, "app", app);
    }

    /** {@code ng:screen:<app>/<đường dẫn route>} — đường dẫn bắt đầu bằng '/'. */
    public static String ngScreen(String app, String routePath) {
        return join(ANGULAR, "screen", app + (routePath.startsWith("/") ? "" : "/") + routePath);
    }

    /** {@code ng:dialog:<app>/<class>} */
    public static String ngDialog(String app, String className) {
        return join(ANGULAR, "dialog", app + "/" + className);
    }

    /** {@code ng:form:<app>/<component>#<mã form>} */
    public static String ngForm(String app, String component, String formId) {
        return join(ANGULAR, "form", app + "/" + component + "#" + formId);
    }

    /** {@code ng:tab:<app>/<component>#<mã tab>} */
    public static String ngTab(String app, String component, String tabId) {
        return join(ANGULAR, "tab", app + "/" + component + "#" + tabId);
    }

    /** {@code ng:method:<app>/<class>#<method>} */
    public static String ngMethod(String app, String className, String methodName) {
        return join(ANGULAR, "method", app + "/" + className + "#" + methodName);
    }

    /** {@code ng:http:<app>/<class>#<method>:<thứ tự>} */
    public static String ngHttpCall(String app, String className, String methodName, int index) {
        return join(ANGULAR, "http", app + "/" + className + "#" + methodName + ":" + index);
    }

    /**
     * {@code ext:system:<tên>} — hệ thống ngoài Workspace do Linker tạo: tên host / service của URL tuyệt đối, hoặc
     * {@link #EXTERNAL_API} khi Workspace không có Backend.
     */
    public static String external(String name) {
        return join(EXTERNAL, "system", name);
    }

    /** Tên hệ thống ngoài khi Workspace chỉ có Frontend. */
    public static final String EXTERNAL_API = "api";

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
