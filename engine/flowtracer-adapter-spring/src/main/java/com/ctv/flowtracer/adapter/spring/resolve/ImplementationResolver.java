package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.resolve.ClassIndex.TypeInfo;
import com.ctv.flowtracer.ir.Confidence;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chọn lớp hiện thực khi một interface / lớp trừu tượng được tiêm vào. Mức tin cậy theo Nhóm 5 của feature_list:
 *
 * <ul>
 *   <li>{@code CERTAIN}: chỉ có một lớp hiện thực là bean, hoặc {@code @Qualifier} tường minh khớp đúng một bean;
 *   <li>{@code INFERRED}: phải dùng luật của Spring — đúng một {@code @Primary}, hoặc tên field trùng tên một bean;
 *   <li>{@code UNRESOLVED}: nhiều bean mà không luật nào phân định (hoặc không có lớp hiện thực): lưu danh sách ứng viên,
 *       KHÔNG chọn hộ.
 * </ul>
 */
final class ImplementationResolver {

    /** Annotation biến một lớp thành bean Spring. */
    private static final Set<String> BEAN_ANNOTATIONS =
            Set.of("Service", "Component", "Repository", "RestController", "Controller", "Configuration");

    /**
     * Một lớp ứng viên.
     *
     * @param type FQN lớp hiện thực
     * @param score độ khớp 0..1 (dùng để xếp hạng và chia mức Cao / Trung bình / Thấp)
     * @param reason căn cứ
     */
    record Candidate(String type, double score, String reason) {}

    /**
     * Kết quả chọn.
     *
     * @param target FQN lớp được chọn; null khi {@code UNRESOLVED}
     * @param reason lý do của mức tin cậy
     * @param candidates mọi ứng viên (rỗng khi chọn được chắc chắn bằng cách duy nhất)
     */
    record Choice(Confidence confidence, String target, String reason, List<Candidate> candidates) {}

    private final ClassIndex index;

    ImplementationResolver(ClassIndex index) {
        this.index = index;
    }

    /** Các lớp hiện thực cụ thể của {@code fqn} (mọi lớp, kể cả chưa là bean), sắp theo FQN. */
    List<String> implementationsOf(String fqn) {
        return index.concreteSubtypes(fqn);
    }

    /**
     * @param declared kiểu khai báo của field tiêm vào (interface, lớp trừu tượng hoặc lớp cụ thể)
     * @param fieldName tên field tiêm vào (Spring dùng làm tên bean dự phòng)
     * @param qualifier tên bean chỉ định rõ, null nếu không có
     */
    Choice choose(String declared, String fieldName, String qualifier) {
        TypeInfo info = index.typeInfo(declared).orElseThrow();
        if (!info.isAbstract()) {
            return new Choice(Confidence.CERTAIN, declared, null, List.of());
        }
        List<String> all = implementationsOf(declared);
        List<String> beans = all.stream().filter(this::isBean).toList();
        String simple = TypeNames.simple(declared);
        if (beans.isEmpty() && all.size() == 1) {
            return new Choice(
                    Confidence.INFERRED,
                    all.getFirst(),
                    simple + " chỉ có một lớp hiện thực là " + TypeNames.simple(all.getFirst())
                            + ", nhưng lớp này không có annotation tạo bean (@Service, @Component…) nên có thể được tạo bằng @Bean",
                    List.of());
        }
        if (beans.isEmpty()) {
            String reason = all.isEmpty()
                    ? simple + " không có lớp hiện thực nào trong Source này"
                    : simple + " có " + all.size() + " lớp hiện thực nhưng không lớp nào có annotation tạo bean";
            return new Choice(Confidence.UNRESOLVED, null, reason, candidates(all, null));
        }
        if (beans.size() == 1) {
            return new Choice(
                    Confidence.CERTAIN,
                    beans.getFirst(),
                    simple + " chỉ có một lớp hiện thực là bean: " + TypeNames.simple(beans.getFirst()),
                    List.of());
        }
        if (qualifier != null) {
            List<String> named = beans.stream().filter(b -> beanNames(b).contains(qualifier)).toList();
            if (named.size() == 1) {
                return new Choice(
                        Confidence.CERTAIN,
                        named.getFirst(),
                        "@Qualifier(\"" + qualifier + "\") khớp đúng " + TypeNames.simple(named.getFirst()),
                        candidates(beans, named.getFirst()));
            }
            return new Choice(
                    Confidence.UNRESOLVED,
                    null,
                    "@Qualifier(\"" + qualifier + "\") " + (named.isEmpty() ? "không khớp lớp nào" : "khớp nhiều lớp"),
                    candidates(beans, null));
        }
        List<String> primary = beans.stream()
                .filter(b -> index.typeInfo(b).orElseThrow().hasAnnotation("Primary"))
                .toList();
        if (primary.size() == 1) {
            return new Choice(
                    Confidence.INFERRED,
                    primary.getFirst(),
                    simple + " có " + beans.size() + " lớp hiện thực; chọn " + TypeNames.simple(primary.getFirst())
                            + " vì được đánh dấu @Primary",
                    candidates(beans, primary.getFirst()));
        }
        List<String> sameName = fieldName == null ? List.<String>of() : beans.stream().filter(b -> beanNames(b).contains(fieldName)).toList();
        if (sameName.size() == 1) {
            return new Choice(
                    Confidence.INFERRED,
                    sameName.getFirst(),
                    simple + " có " + beans.size() + " lớp hiện thực; chọn " + TypeNames.simple(sameName.getFirst())
                            + " vì tên field \"" + fieldName + "\" trùng tên bean (Spring dùng tên field khi không có @Primary)",
                    candidates(beans, sameName.getFirst()));
        }
        return new Choice(
                Confidence.UNRESOLVED,
                null,
                simple + " có " + beans.size() + " lớp hiện thực; không có @Primary hay @Qualifier nào phân định lớp được dùng",
                candidates(beans, null));
    }

    private boolean isBean(String fqn) {
        return index.typeInfo(fqn).filter(t -> BEAN_ANNOTATIONS.stream().anyMatch(t::hasAnnotation)).isPresent();
    }

    /** Tên bean của lớp: giá trị khai ở annotation, {@code @Qualifier} trên lớp, hoặc tên lớp viết thường chữ đầu. */
    private Set<String> beanNames(String fqn) {
        TypeInfo info = index.typeInfo(fqn).orElseThrow();
        var names = new java.util.TreeSet<String>();
        for (String a : List.of("Service.value", "Component.value", "Repository.value", "Qualifier.value")) {
            String v = info.type().annotationValues().get(a);
            if (v != null && !v.isBlank() && !v.startsWith("@const:")) {
                names.add(v);
            }
        }
        String simple = TypeNames.simple(fqn);
        names.add(simple.substring(0, 1).toLowerCase(Locale.ROOT) + simple.substring(1));
        return names;
    }

    /** Ứng viên có điểm: lớp được chọn 0.9; còn lại 0.3. Không chọn được thì 0.5, trừ 0.1 nếu có điều kiện kích hoạt. */
    private List<Candidate> candidates(List<String> types, String chosen) {
        var result = new ArrayList<Candidate>();
        for (String type : types) {
            TypeInfo info = index.typeInfo(type).orElseThrow();
            String condition = condition(info);
            double score = chosen == null ? 0.5 : type.equals(chosen) ? 0.9 : 0.3;
            if (chosen == null && condition != null) {
                score -= 0.1;
            }
            String reason = chosen != null && type.equals(chosen)
                    ? "Lớp được chọn"
                    : condition != null ? "Chỉ tạo khi " + condition : "Lớp hiện thực " + TypeNames.simple(type);
            result.add(new Candidate(type, score, reason));
        }
        result.sort(java.util.Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Candidate::type));
        return result;
    }

    /** Điều kiện kích hoạt bean ({@code @ConditionalOnProperty}, {@code @Profile}); null nếu không có. */
    private static String condition(TypeInfo info) {
        var values = info.type().annotationValues();
        if (info.hasAnnotation("ConditionalOnProperty")) {
            String name = values.getOrDefault("ConditionalOnProperty.name", values.getOrDefault("ConditionalOnProperty.value", "?"));
            return "@ConditionalOnProperty(" + name + ")";
        }
        if (info.hasAnnotation("Profile")) {
            return "@Profile(" + values.getOrDefault("Profile.value", "?") + ")";
        }
        return null;
    }
}
