package com.ctv.flowtracer.app.workspace.service;

import com.ctv.flowtracer.app.workspace.entity.Source;
import com.ctv.flowtracer.core.detection.FolderDetector;
import com.ctv.flowtracer.ir.SourceRole;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Quy tắc Source lồng nhau (Nhóm 1): Source con nằm trong Source khác thì Source cha tự loại thư mục con, để mỗi file
 * chỉ được phân tích một lần. Quy tắc thuần, không truy cập CSDL.
 */
final class NestedSourceRules {

    private NestedSourceRules() {}

    /** Đã có Source cùng thư mục và cùng vai trò. */
    static boolean isDuplicate(List<Source> existing, String rootPath, SourceRole role) {
        return existing.stream().anyMatch(s -> s.getRootPath().equals(rootPath) && s.getRole() == role);
    }

    /** Luật loại trừ Source cha cần có để bỏ qua Source con, vd {@code frontend/**}. */
    static String excludeGlobFor(Source child, Source parent) {
        String parentPath = parent.getRootPath();
        String rel = parentPath.isEmpty() ? child.getRootPath() : child.getRootPath().substring(parentPath.length() + 1);
        return rel + "/**";
    }

    /** Source chứa {@code child} gần nhất (thư mục sâu nhất), nếu có. */
    static Optional<Source> nearestParent(Source child, List<Source> all) {
        return all.stream()
                .filter(s -> s != child && FolderDetector.isStrictlyInside(child.getRootPath(), s.getRootPath()))
                .max(Comparator.comparingInt((Source s) -> s.getRootPath().length()));
    }

    /**
     * Tính lại Source cha cho mọi Source và bảo đảm Source cha có luật loại trừ Source con. Không xóa luật người dùng tự
     * nhập.
     */
    static void apply(List<Source> all) {
        for (Source child : all) {
            Source parent = nearestParent(child, all).orElse(null);
            child.setParent(parent);
            if (parent != null) {
                addExclude(parent, excludeGlobFor(child, parent));
            }
        }
    }

    /** Gỡ luật loại trừ đã thêm vào Source cha khi Source con bị xóa. */
    static void detach(Source removed, List<Source> all) {
        nearestParent(removed, all).ifPresent(parent -> {
            var globs = new ArrayList<>(parent.getExcludeGlobs());
            globs.remove(excludeGlobFor(removed, parent));
            parent.setExcludeGlobs(globs);
        });
    }

    /** Luật loại trừ bắt buộc của một Source (do có Source con) — giữ lại khi người dùng sửa danh sách loại trừ. */
    static List<String> requiredExcludes(Source parent, List<Source> all) {
        return all.stream()
                .filter(s -> s != parent && nearestParent(s, all).orElse(null) == parent)
                .map(s -> excludeGlobFor(s, parent))
                .sorted()
                .toList();
    }

    private static void addExclude(Source source, String glob) {
        if (!source.getExcludeGlobs().contains(glob)) {
            var globs = new ArrayList<>(source.getExcludeGlobs());
            globs.add(glob);
            source.setExcludeGlobs(globs);
        }
    }
}
