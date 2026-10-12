package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Giải giá trị annotation viết bằng tham chiếu hằng số ({@code @const:Lớp.TÊN}) về chuỗi, theo bảng hằng của Source. */
final class ConstantTable {

    private final Map<String, String> constants;

    ConstantTable(List<JavaFileFacts> facts) {
        this.constants = EndpointBuilder.constants(facts);
    }

    /**
     * @param value giá trị lấy từ {@code annotationValues}: chuỗi thường trả nguyên văn; {@code @const:…} được giải
     * @param declaringType FQN lớp chứa annotation (để thử hằng cùng lớp trước)
     * @return rỗng nếu là tham chiếu không giải được
     */
    Optional<String> resolve(String value, JavaFileFacts file, String declaringType) {
        if (value == null) {
            return Optional.empty();
        }
        if (!value.startsWith(JavaFileFacts.CONSTANT_REF_PREFIX)) {
            return Optional.of(value);
        }
        String ref = value.substring(JavaFileFacts.CONSTANT_REF_PREFIX.length());
        String found = constants.get(declaringType + "." + ref);
        if (found == null) {
            found = constants.get(ref);
        }
        if (found == null) {
            int dot = ref.lastIndexOf('.');
            if (dot > 0) {
                found = imported(file, ref.substring(0, dot), ref.substring(dot + 1));
            }
        }
        return Optional.ofNullable(found);
    }

    private String imported(JavaFileFacts file, String owner, String name) {
        for (String imp : file.imports()) {
            if (imp.endsWith("." + owner)) {
                String value = constants.get(imp + "." + name);
                if (value != null) {
                    return value;
                }
            }
        }
        return constants.get(file.packageName() + "." + owner + "." + name);
    }
}
