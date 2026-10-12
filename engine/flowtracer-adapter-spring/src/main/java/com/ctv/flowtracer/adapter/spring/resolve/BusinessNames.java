package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.resolve.ClassIndex.TypeInfo;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tên nghiệp vụ của một lớp, dùng làm nhãn cột diagram ("Service Danh mục"): {@code @Tag} của Controller cùng tên gốc
 * (bỏ hậu tố {@code Controller/Service/Repository/Impl…}), nếu không có thì chính tên gốc của lớp.
 */
final class BusinessNames {

    /** Nguồn tên nghiệp vụ. */
    enum Source {
        TAG,
        CLASS
    }

    record Name(String text, Source source) {}

    private static final List<String> SUFFIXES = List.of(
            "RestController", "Controller", "Resource", "UseCase", "Service", "Repository", "Dao", "Facade", "Api");

    private final Map<String, String> tagByBase = new TreeMap<>();

    BusinessNames(ClassIndex index, LayerClassifier layers) {
        for (TypeInfo info : index.allTypes()) {
            if (layers.classify(info.fqn()).layer() == LayerClassifier.Layer.CONTROLLER) {
                String tag = info.type().annotationValues().get("Tag.name");
                if (tag != null && !tag.isBlank() && !tag.startsWith("@const:")) {
                    tagByBase.putIfAbsent(base(TypeNames.simple(info.fqn())), tag.strip());
                }
            }
        }
    }

    Name of(String fqn) {
        String base = base(TypeNames.simple(fqn));
        String tag = tagByBase.get(base);
        return tag != null ? new Name(tag, Source.TAG) : new Name(base, Source.CLASS);
    }

    /** {@code CategoryServiceImpl} → {@code Category}; không còn gì sau khi bỏ hậu tố thì giữ nguyên tên. */
    static String base(String simpleName) {
        String name = simpleName.substring(simpleName.lastIndexOf('$') + 1);
        if (name.endsWith("Impl") && name.length() > 4) {
            name = name.substring(0, name.length() - 4);
        }
        for (String suffix : SUFFIXES) {
            if (name.endsWith(suffix) && name.length() > suffix.length()) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return name;
    }
}
