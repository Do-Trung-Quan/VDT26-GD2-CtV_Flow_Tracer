package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.resolve.ClassIndex.TypeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Entity → tên bảng. {@code @Table(name)} nếu có; nếu không thì tên entity theo quy ước đặt tên mặc định của Spring Boot
 * (camelCase → snake_case, chữ thường) — kết quả này chỉ là suy ra vì cấu hình có thể đổi quy ước đặt tên.
 */
final class EntityTableResolver {

    /** Căn cứ của tên bảng. */
    enum Basis {
        /** Khai tường minh bằng {@code @Table(name = …)}. */
        ANNOTATION,
        /** Suy ra từ tên entity theo quy ước đặt tên. */
        NAMING
    }

    /**
     * @param name tên bảng viết thường
     * @param entityFqn FQN lớp entity
     * @param entity tên ngắn của entity
     */
    record Table(String name, String entityFqn, String entity, Basis basis) {}

    private final ClassIndex index;
    private final ConstantTable constants;
    private final Map<String, List<String>> entitiesByName = new TreeMap<>();

    EntityTableResolver(ClassIndex index, ConstantTable constants) {
        this.index = index;
        this.constants = constants;
        for (TypeInfo info : index.allTypes()) {
            if (isEntity(info)) {
                entitiesByName.computeIfAbsent(entityName(info), k -> new ArrayList<>()).add(info.fqn());
            }
        }
    }

    static boolean isEntity(TypeInfo info) {
        return info.hasAnnotation("Entity") || info.hasAnnotation("Table");
    }

    /** Tên entity trong JPQL: {@code @Entity(name)} hoặc tên ngắn của lớp. */
    private String entityName(TypeInfo info) {
        String declared = info.type().annotationValues().get("Entity.name");
        if (declared != null && !declared.isBlank() && !declared.startsWith("@const:")) {
            return declared;
        }
        return TypeNames.simple(info.fqn());
    }

    /** Các entity có tên (JPQL) đúng như viết; thường chỉ một. */
    List<String> entitiesNamed(String name) {
        return entitiesByName.getOrDefault(name, List.of());
    }

    Optional<Table> forEntity(String entityFqn) {
        return index.typeInfo(entityFqn).map(this::table);
    }

    private Table table(TypeInfo info) {
        var values = info.type().annotationValues();
        String declared = values.containsKey("Table.name") ? values.get("Table.name") : values.get("Table.value");
        Optional<String> explicit =
                constants.resolve(declared, info.file(), info.fqn()).filter(s -> !s.isBlank());
        String simple = TypeNames.simple(info.fqn());
        if (explicit.isPresent()) {
            return new Table(explicit.get().toLowerCase(Locale.ROOT), info.fqn(), simple, Basis.ANNOTATION);
        }
        return new Table(snakeCase(entityName(info)), info.fqn(), simple, Basis.NAMING);
    }

    /** Quy ước mặc định của Spring Boot: {@code ProductCategory} → {@code product_category}. */
    static String snakeCase(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }
}
