package com.ctv.flowtracer.adapter.spring.resolve;

import java.util.List;

/**
 * Tên module của một endpoint (Nhóm 3): {@code @Tag} của Swagger → tên Controller → tên package. Kết quả luôn kèm nguồn
 * để giao diện biết tên module lấy từ đâu.
 */
public final class ModuleNameResolver {

    /** Nguồn của tên module. */
    public enum Source {
        TAG,
        CONTROLLER,
        PACKAGE
    }

    /** Tên module và nguồn của nó. */
    public record Module(String name, Source source) {}

    private static final List<String> CONTROLLER_SUFFIXES = List.of("RestController", "Controller", "Resource", "Api");

    /**
     * @param tag tên {@code @Tag} (của method qua {@code @Operation(tags)}, hoặc của class), null nếu không có
     * @param controllerFqn FQN của lớp Controller
     */
    public Module resolve(String tag, String controllerFqn) {
        if (tag != null && !tag.isBlank()) {
            return new Module(tag.strip(), Source.TAG);
        }
        int dot = controllerFqn.lastIndexOf('.');
        String simpleName = controllerFqn.substring(dot + 1);
        String trimmed = stripSuffix(simpleName);
        if (!trimmed.isEmpty()) {
            return new Module(trimmed, Source.CONTROLLER);
        }
        String packageName = dot < 0 ? "" : controllerFqn.substring(0, dot);
        String lastSegment = packageName.substring(packageName.lastIndexOf('.') + 1);
        return new Module(lastSegment.isEmpty() ? simpleName : lastSegment, Source.PACKAGE);
    }

    /** {@code CategoryController} → {@code Category}; {@code Controller} → {@code ""}. */
    static String stripSuffix(String simpleName) {
        String nested = simpleName.substring(simpleName.lastIndexOf('$') + 1);
        for (String suffix : CONTROLLER_SUFFIXES) {
            if (nested.endsWith(suffix)) {
                return nested.substring(0, nested.length() - suffix.length());
            }
        }
        return nested;
    }
}
