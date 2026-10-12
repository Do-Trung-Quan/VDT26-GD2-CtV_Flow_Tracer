package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.MethodFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.StringFragmentFact;
import com.ctv.flowtracer.adapter.spring.resolve.ClassIndex.TypeInfo;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.SourceLocation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Nhận ra thao tác dữ liệu của một method và các bảng nó đụng tới:
 *
 * <ul>
 *   <li>method của repository Spring Data: {@code @Query} (JPQL / native), tên suy ra truy vấn ({@code findByCode},
 *       {@code existsBy…}), hoặc method kế thừa ({@code save}, {@code findById}…) — bảng lấy từ entity của repository;
 *   <li>lời gọi vào điểm thực thi truy vấn ({@code EntityManager}, {@code JdbcTemplate}, hoặc luật QUERY_SINK do người
 *       dùng khai) — bảng suy ra từ các mảnh chuỗi trong method.
 * </ul>
 *
 * Không đoán: không đọc được văn bản truy vấn thì không có bảng, và kết quả nói rõ lý do.
 */
final class DataAccessResolver {

    /** Loại điểm thực thi truy vấn nhận ra từ lời gọi. */
    enum SinkKind {
        JPQL,
        NATIVE,
        /** Hàm thư viện do người dùng khai báo bằng luật QUERY_SINK. */
        LIB_CALL,
        /**
         * Hàm của một kiểu thư viện không có mã nguồn mà chỉ đoán là điểm thực thi truy vấn, vì tên kiểu giống lớp truy cập
         * dữ liệu ({@code …Repository}, {@code …Dao}, {@code …Template}) và nó được gọi từ lớp Repository của dự án.
         */
        GUESSED
    }

    /**
     * Một bảng mà truy vấn đụng tới.
     *
     * @param entity tên ngắn của entity; null nếu không có entity tương ứng
     * @param confidence mức tin cậy của liên kết truy vấn → bảng
     * @param reason lý do khi không phải {@code CERTAIN} hoặc khi bảng không có vị trí mã nguồn
     * @param location vị trí khai báo entity; null nếu bảng chỉ suy ra từ văn bản truy vấn
     */
    record TableRef(String name, String entity, Confidence confidence, String reason, SourceLocation location) {}

    /**
     * Thao tác dữ liệu của một method.
     *
     * @param kind {@code DERIVED}, {@code JPQL}, {@code NATIVE}, {@code DYNAMIC}, {@code LIB_CALL}
     * @param text văn bản truy vấn đã rút gọn; null nếu không có
     * @param verb {@code SELECT}, {@code SELECT COUNT(*)}, {@code INSERT/UPDATE}, {@code UPDATE}, {@code DELETE}…
     * @param operation {@code READ}, {@code WRITE} hoặc {@code DELETE}
     * @param confidence mức tin cậy của việc method này thực thi truy vấn
     * @param reason lý do khi không {@code CERTAIN}
     */
    record QueryInfo(
            String kind,
            String text,
            String verb,
            String operation,
            List<TableRef> tables,
            Confidence confidence,
            String reason) {}

    private static final Pattern DERIVED_READ = Pattern.compile("^(find|get|read|query|search|stream)([A-Z].*)?$");
    private static final int MAX_TEXT = 300;

    /** Mảnh chuỗi chỉ là mệnh đề (WHERE / ORDER BY / JOIN…), không tự là một câu lệnh: nối được vào câu của hàm khác. */
    private static final Pattern STATEMENT_VERB = Pattern.compile("(?i)\\b(select|insert|update|delete)\\b");

    static boolean isStatement(String text) {
        return STATEMENT_VERB.matcher(text).find();
    }

    private final ClassIndex index;
    private final EntityTableResolver entities;
    private final ConstantTable constants;
    private final QueryTextAnalyzer analyzer = new QueryTextAnalyzer();

    DataAccessResolver(ClassIndex index, EntityTableResolver entities, ConstantTable constants) {
        this.index = index;
        this.entities = entities;
        this.constants = constants;
    }

    /**
     * Thao tác dữ liệu của method khai báo trong repository (hoặc method kế thừa khi {@code declared == null}).
     *
     * @param declared method khai báo trong interface; null nếu là method kế thừa từ Spring Data
     */
    Optional<QueryInfo> repositoryQuery(TypeInfo repo, String name, int paramCount, MethodFact declared) {
        Optional<EntityTableResolver.Table> entity = entityOf(repo);
        if (declared == null) {
            return SpringDataMethods.find(name, paramCount)
                    .map(c -> derived(repo, entity, c.verb(), c.operation()));
        }
        String queryRaw = declared.annotationValues().get("Query.value");
        if (declared.annotations().contains("Query") && queryRaw != null) {
            boolean nativeQuery = "true".equals(declared.annotationValues().get("Query.nativeQuery"));
            Optional<String> text = constants.resolve(queryRaw, repo.file(), repo.fqn());
            if (text.isPresent()) {
                return Optional.of(fromText(nativeQuery ? "NATIVE" : "JPQL", text.get(), nativeQuery, repo, false));
            }
            QueryInfo fallback = derived(repo, entity, null, null);
            return Optional.of(new QueryInfo(
                    nativeQuery ? "NATIVE" : "JPQL",
                    null,
                    null,
                    null,
                    fallback.tables(),
                    Confidence.INFERRED,
                    "Văn bản @Query là tham chiếu hằng số không giải được; bảng lấy từ entity của repository"));
        }
        if (declared.hasBody()) {
            return Optional.empty();
        }
        if (name.startsWith("count")) {
            return Optional.of(derived(repo, entity, "SELECT COUNT(*)", "READ"));
        }
        if (name.startsWith("exists") || DERIVED_READ.matcher(name).matches()) {
            return Optional.of(derived(repo, entity, "SELECT", "READ"));
        }
        if (name.startsWith("delete") || name.startsWith("remove")) {
            return Optional.of(derived(repo, entity, "DELETE", "DELETE"));
        }
        return Optional.empty();
    }

    /**
     * Thao tác dữ liệu của một lời gọi vào điểm thực thi truy vấn.
     *
     * @param fragments các mảnh chuỗi trông như truy vấn trong method, đã sắp theo thứ tự xuất hiện
     */
    QueryInfo sinkQuery(SinkKind sink, List<StringFragmentFact> fragments, String sinkName) {
        if (fragments.isEmpty()) {
            return new QueryInfo(
                    "LIB_CALL",
                    null,
                    null,
                    null,
                    List.of(),
                    sink == SinkKind.LIB_CALL ? Confidence.CERTAIN : Confidence.INFERRED,
                    sink == SinkKind.GUESSED
                            ? sinkName + " là hàm của thư viện không có mã nguồn, được nhận là truy vấn dữ liệu theo tên kiểu; "
                                    + "không đọc được câu truy vấn nên chưa biết bảng nào được đụng tới"
                            : "Không tìm thấy văn bản truy vấn trong thân method gọi " + sinkName + "; chưa biết bảng nào được đụng tới");
        }
        boolean dynamic = fragments.size() > 1 || fragments.stream().anyMatch(StringFragmentFact::conditional);
        String text = String.join(" ", fragments.stream().map(StringFragmentFact::text).toList());
        String kind = dynamic ? "DYNAMIC" : sink == SinkKind.NATIVE ? "NATIVE" : sink == SinkKind.JPQL ? "JPQL" : "DYNAMIC";
        QueryInfo info = fromText(kind, text, sink == SinkKind.NATIVE, null, dynamic);
        if (sink == SinkKind.GUESSED) {
            return new QueryInfo(
                    info.kind(),
                    info.text(),
                    info.verb(),
                    info.operation(),
                    info.tables(),
                    Confidence.INFERRED,
                    sinkName + " là hàm của thư viện không có mã nguồn, được nhận là truy vấn dữ liệu theo tên kiểu; bảng suy ra từ "
                            + fragments.size() + " mảnh chuỗi truy vấn trong lớp");
        }
        if (!dynamic) {
            return info;
        }
        long conditional = fragments.stream().filter(StringFragmentFact::conditional).count();
        return new QueryInfo(
                info.kind(),
                info.text(),
                info.verb(),
                info.operation(),
                info.tables(),
                Confidence.INFERRED,
                "Câu truy vấn ghép từ " + fragments.size() + " mảnh chuỗi (" + conditional
                        + " mảnh có điều kiện); bảng suy ra từ các mảnh này");
    }

    private QueryInfo fromText(String kind, String text, boolean nativeQuery, TypeInfo repo, boolean dynamic) {
        QueryTextAnalyzer.Analysis analysis = analyzer.analyze(text);
        var tables = new ArrayList<TableRef>();
        for (QueryTextAnalyzer.Identifier id : analysis.identifiers()) {
            tableOf(id, nativeQuery, dynamic).ifPresent(t -> {
                if (tables.stream().noneMatch(x -> x.name().equals(t.name()))) {
                    tables.add(t);
                }
            });
        }
        return new QueryInfo(
                kind, shorten(text), analysis.verb(), analysis.operation(), tables, Confidence.CERTAIN, null);
    }

    private Optional<TableRef> tableOf(QueryTextAnalyzer.Identifier id, boolean nativeQuery, boolean dynamic) {
        String name = id.name();
        if (!nativeQuery && id.joined() && name.contains(".")) {
            return Optional.empty(); // JPQL: đường dẫn liên kết như c.products, không phải entity
        }
        String last = name.substring(name.lastIndexOf('.') + 1);
        if (!nativeQuery || dynamic) {
            List<String> found = entities.entitiesNamed(last);
            if (!found.isEmpty()) {
                EntityTableResolver.Table t = entities.forEntity(found.getFirst()).orElseThrow();
                String reason = found.size() > 1
                        ? "Có " + found.size() + " entity cùng tên " + last + "; lấy " + TypeNames.simple(found.getFirst())
                        : t.basis() == EntityTableResolver.Basis.NAMING ? namingReason(t) : null;
                Confidence c = reason == null && !dynamic ? Confidence.CERTAIN : Confidence.INFERRED;
                return Optional.of(entityRef(t, c, reason));
            }
        }
        if (!nativeQuery && !dynamic) {
            return Optional.of(new TableRef(
                    last.toLowerCase(Locale.ROOT),
                    null,
                    Confidence.INFERRED,
                    "Không tìm thấy entity \"" + last + "\" trong mã nguồn; dùng nguyên tên làm tên bảng",
                    null));
        }
        String reason = "Tên bảng đọc từ văn bản truy vấn" + (dynamic ? " ghép động" : "");
        return Optional.of(new TableRef(
                last.toLowerCase(Locale.ROOT), null, dynamic ? Confidence.INFERRED : Confidence.CERTAIN, reason, null));
    }

    private QueryInfo derived(TypeInfo repo, Optional<EntityTableResolver.Table> entity, String verb, String operation) {
        List<TableRef> tables = entity.map(t -> List.of(entityRef(
                        t,
                        t.basis() == EntityTableResolver.Basis.ANNOTATION ? Confidence.CERTAIN : Confidence.INFERRED,
                        t.basis() == EntityTableResolver.Basis.NAMING ? namingReason(t) : null)))
                .orElse(List.of());
        return new QueryInfo(
                "DERIVED",
                null,
                verb,
                operation,
                tables,
                Confidence.CERTAIN,
                entity.isEmpty() ? "Không xác định được entity của " + TypeNames.simple(repo.fqn()) + "; chưa biết bảng" : null);
    }

    private TableRef entityRef(EntityTableResolver.Table t, Confidence confidence, String reason) {
        SourceLocation location = index.typeInfo(t.entityFqn())
                .map(i -> SourceLocation.of(i.file().relativePath(), i.type().line()))
                .orElse(null);
        return new TableRef(t.name(), t.entity(), confidence, reason, location);
    }

    private static String namingReason(EntityTableResolver.Table t) {
        return "Entity " + t.entity() + " không khai @Table(name); tên bảng suy ra theo quy ước đặt tên mặc định của Spring Boot";
    }

    /**
     * Entity của repository: đối số generic đầu tiên là entity trong dự án của một interface cha (chính nó hoặc tổ tiên
     * trong dự án), vd {@code JpaRepository<Category, Long>}.
     */
    Optional<EntityTableResolver.Table> entityOf(TypeInfo repo) {
        var chain = new ArrayList<String>();
        chain.add(repo.fqn());
        chain.addAll(index.ancestors(repo.fqn()));
        for (String fqn : chain) {
            TypeInfo info = index.typeInfo(fqn).orElse(null);
            if (info == null) {
                continue;
            }
            for (String supertype : info.type().interfaces()) {
                for (String arg : TypeNames.typeArguments(supertype)) {
                    Optional<TypeInfo> candidate =
                            index.resolveSimpleName(arg, info.file()).flatMap(index::typeInfo);
                    if (candidate.isPresent() && EntityTableResolver.isEntity(candidate.get())) {
                        return entities.forEntity(candidate.get().fqn());
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Mảnh chuỗi của một method, theo thứ tự dòng. */
    static List<StringFragmentFact> inOrder(List<StringFragmentFact> fragments) {
        return fragments.stream().sorted(Comparator.comparingInt(StringFragmentFact::line)).toList();
    }

    private static String shorten(String text) {
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= MAX_TEXT ? flat : flat.substring(0, MAX_TEXT - 1) + "…";
    }
}
