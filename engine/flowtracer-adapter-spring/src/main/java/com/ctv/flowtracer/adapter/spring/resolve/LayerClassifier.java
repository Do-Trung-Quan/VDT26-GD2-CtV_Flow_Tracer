package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.extract.MappingExtractor;
import com.ctv.flowtracer.adapter.spring.resolve.ClassIndex.TypeInfo;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Xếp một lớp vào tầng Controller / Service / Repository / khác. Thứ tự căn cứ: annotation → kế thừa interface Spring
 * Data → lớp hiện thực của một interface (với interface / lớp trừu tượng) → hậu tố tên. Kết quả luôn kèm căn cứ để người
 * dùng biết tầng được suy ra từ đâu.
 */
final class LayerClassifier {

    /** Tầng xử lý. */
    enum Layer {
        CONTROLLER,
        SERVICE,
        REPOSITORY,
        OTHER
    }

    /** Căn cứ xếp tầng. */
    enum Basis {
        ANNOTATION,
        INHERITANCE,
        IMPLEMENTATION,
        NAME,
        NONE
    }

    record Classification(Layer layer, Basis basis) {}

    /** Interface gốc của Spring Data mà một repository kế thừa. */
    static final Set<String> SPRING_DATA_BASES = Set.of(
            "Repository",
            "CrudRepository",
            "ListCrudRepository",
            "PagingAndSortingRepository",
            "ListPagingAndSortingRepository",
            "JpaRepository",
            "JpaSpecificationExecutor",
            "QueryByExampleExecutor",
            "MongoRepository",
            "ReactiveCrudRepository",
            "ReactiveMongoRepository",
            "ElasticsearchRepository",
            "R2dbcRepository");

    private static final List<String> SERVICE_SUFFIXES = List.of("ServiceImpl", "Service", "UseCase", "UseCaseImpl");
    private static final List<String> REPOSITORY_SUFFIXES = List.of("RepositoryImpl", "Repository", "DaoImpl", "Dao");

    private final ClassIndex index;
    private final Map<String, Classification> cache = new TreeMap<>();

    LayerClassifier(ClassIndex index) {
        this.index = index;
    }

    Classification classify(String fqn) {
        Classification cached = cache.get(fqn);
        if (cached == null) {
            cached = index.typeInfo(fqn).map(this::compute).orElse(new Classification(Layer.OTHER, Basis.NONE));
            cache.put(fqn, cached);
        }
        return cached;
    }

    /** Lớp là repository Spring Data (interface kế thừa một interface gốc của Spring Data). */
    boolean isSpringData(String fqn) {
        return index.typeInfo(fqn).filter(TypeInfo::isInterface).isPresent()
                && index.rawSupertypeNames(fqn).stream().anyMatch(SPRING_DATA_BASES::contains);
    }

    private Classification compute(TypeInfo info) {
        if (MappingExtractor.CONTROLLER_ANNOTATIONS.stream().anyMatch(info::hasAnnotation)) {
            return new Classification(Layer.CONTROLLER, Basis.ANNOTATION);
        }
        if (info.hasAnnotation("Repository")) {
            return new Classification(Layer.REPOSITORY, Basis.ANNOTATION);
        }
        if (info.hasAnnotation("Service")) {
            return new Classification(Layer.SERVICE, Basis.ANNOTATION);
        }
        if (isSpringData(info.fqn())) {
            return new Classification(Layer.REPOSITORY, Basis.INHERITANCE);
        }
        if (info.isAbstract()) {
            for (String sub : index.concreteSubtypes(info.fqn())) {
                Classification c = index.typeInfo(sub).map(this::annotationOnly).orElse(null);
                if (c != null) {
                    return new Classification(c.layer(), Basis.IMPLEMENTATION);
                }
            }
        }
        String simple = TypeNames.simple(info.fqn());
        if (SERVICE_SUFFIXES.stream().anyMatch(simple::endsWith)) {
            return new Classification(Layer.SERVICE, Basis.NAME);
        }
        if (REPOSITORY_SUFFIXES.stream().anyMatch(simple::endsWith)) {
            return new Classification(Layer.REPOSITORY, Basis.NAME);
        }
        return new Classification(Layer.OTHER, Basis.NONE);
    }

    /** Tầng chỉ dựa vào annotation (dùng để suy tầng của interface từ lớp hiện thực); null nếu không có. */
    private Classification annotationOnly(TypeInfo info) {
        if (info.hasAnnotation("Repository")) {
            return new Classification(Layer.REPOSITORY, Basis.ANNOTATION);
        }
        if (info.hasAnnotation("Service")) {
            return new Classification(Layer.SERVICE, Basis.ANNOTATION);
        }
        return null;
    }
}
