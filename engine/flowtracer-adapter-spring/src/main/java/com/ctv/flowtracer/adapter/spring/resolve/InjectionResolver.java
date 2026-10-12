package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.FieldFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.MethodFact;
import com.ctv.flowtracer.adapter.spring.resolve.ClassIndex.TypeInfo;
import java.util.Optional;

/**
 * Phụ thuộc được tiêm vào một lớp: tra field theo tên (kể cả field của lớp cha), kiểu khai báo của field và
 * {@code @Qualifier} / {@code @Resource(name)} áp cho nó. Field {@code final} + Lombok {@code @RequiredArgsConstructor},
 * {@code @Autowired}, hay tham số constructor đều là field thật trong mã nên không cần phân biệt kiểu tiêm.
 */
final class InjectionResolver {

    /**
     * Một field tiêm vào.
     *
     * @param owner lớp khai báo field
     * @param field field
     * @param type FQN kiểu khai báo nếu là kiểu trong dự án; null nếu là kiểu thư viện
     * @param typeName tên kiểu như viết trong mã (đã bỏ generic)
     * @param qualifier tên bean được chỉ định rõ ({@code @Qualifier}, {@code @Resource(name)}); null nếu không có
     */
    record Injection(TypeInfo owner, FieldFact field, String type, String typeName, String qualifier) {}

    private final ClassIndex index;

    InjectionResolver(ClassIndex index) {
        this.index = index;
    }

    Optional<Injection> find(String classFqn, String fieldName) {
        return index.findField(classFqn, fieldName).flatMap(field -> index.typeInfo(field.declaringType())
                .map(owner -> new Injection(
                        owner,
                        field,
                        index.resolveSimpleName(field.type(), owner.file()).orElse(null),
                        TypeNames.stripGenerics(field.type()),
                        qualifier(owner, field))));
    }

    /** Tên bean chỉ định ở field, hoặc ở tham số constructor cùng tên với field (kiểu {@code this.x = x}). */
    private static String qualifier(TypeInfo owner, FieldFact field) {
        String onField = firstNonBlank(
                field.annotationValues().get("Qualifier.value"), field.annotationValues().get("Resource.name"));
        if (onField != null) {
            return onField;
        }
        for (MethodFact m : owner.methods()) {
            if (!m.name().equals("<init>")) {
                continue;
            }
            int i = m.paramNames().indexOf(field.name());
            if (i >= 0 && i < m.paramQualifiers().size() && !m.paramQualifiers().get(i).isEmpty()) {
                return m.paramQualifiers().get(i);
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank() && !v.startsWith("@const:")) {
                return v;
            }
        }
        return null;
    }
}
