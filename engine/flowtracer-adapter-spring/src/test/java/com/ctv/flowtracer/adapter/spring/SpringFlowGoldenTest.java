package com.ctv.flowtracer.adapter.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Golden test phần dựng luồng xử lý Backend: phân tích {@code fixtures/spring-flow} rồi so với
 * {@code fixtures/golden/spring-flow.ir.json}. Sinh lại snapshot bằng {@code -Pgolden-update}.
 */
class SpringFlowGoldenTest {

    private static final Path GOLDEN = FixtureRunner.FIXTURES.resolve("golden").resolve("spring-flow.ir.json");
    private static final String CATEGORY_SERVICE = "com.example.flow.category.CategoryServiceImpl";
    private static final String CATEGORY_REPOSITORY = "com.example.flow.category.CategoryRepository";

    private final JsonMapper json = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final IrFragment fragment = new FixtureRunner("spring-flow").analyze();

    @Test
    void matchesGoldenSnapshot() throws Exception {
        String actual = json.writeValueAsString(fragment).replace("\r\n", "\n") + "\n";
        if (Boolean.getBoolean("golden.update")) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, actual, StandardCharsets.UTF_8);
        }
        assertThat(actual).isEqualTo(Files.readString(GOLDEN, StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    @Test
    void sameInputGivesSameOutput() throws Exception {
        IrFragment again = new FixtureRunner("spring-flow").analyze();
        assertThat(json.writeValueAsString(again)).isEqualTo(json.writeValueAsString(fragment));
    }

    @Test
    void everyEndpointIsHandledByAControllerMethod() {
        List<IrNode> endpoints = nodes(NodeKind.API_ENDPOINT);

        assertThat(endpoints).hasSize(6);
        for (IrNode endpoint : endpoints) {
            assertThat(edgesFrom(endpoint.key(), EdgeKind.HANDLED_BY))
                    .as(endpoint.name())
                    .singleElement()
                    .satisfies(e -> {
                        assertThat(e.confidence()).isEqualTo(Confidence.CERTAIN);
                        assertThat(e.location()).isNotNull();
                    });
        }
    }

    @Test
    void interfaceWithSingleImplementationIsCertainAndLandsOnTheImplementation() {
        IrEdge call = edge(
                "java:method:com.example.flow.category.CategoryController#create(CategoryRequest)",
                "java:method:" + CATEGORY_SERVICE + "#create(CategoryRequest)");

        assertThat(call.kind()).isEqualTo(EdgeKind.CALLS);
        assertThat(call.confidence()).isEqualTo(Confidence.CERTAIN);
        assertThat(call.reason()).contains("chỉ có một lớp hiện thực");
    }

    @Test
    void callsInsideOneMethodKeepEvaluationOrderAndGuards() {
        String create = "java:method:" + CATEGORY_SERVICE + "#create(CategoryRequest)";
        List<IrEdge> calls = edgesFrom(create, EdgeKind.CALLS);

        assertThat(calls).extracting(e -> simpleTarget(e.toKey())).containsExactly(
                "CategoryServiceImpl#checkPermission()",
                "CategoryServiceImpl#normalize(String)",
                "CategoryRepository#existsByCode(String)",
                "CategoryRepository#save(S)",
                "CategoryServiceImpl#toResponse(Category)");
        assertThat(calls).extracting(IrEdge::callOrder).isSorted().doesNotHaveDuplicates();

        // getter / setter của entity, DTO không thành cạnh
        assertThat(fragment.edges()).noneMatch(e -> e.toKey() != null && (e.toKey().contains("#get") || e.toKey().contains("#set")) && !e.toKey().contains("Service#"));
        // …nhưng hàm getX() của Service là nghiệp vụ thật nên vẫn có cạnh
        assertThat(fragment.edges()).anyMatch(e -> "java:method:com.example.flow.product.ProductService#getOptions()".equals(e.toKey()));
        IrNode create_ = node(create);
        assertThat(create_.attrs().get("throws").toString())
                .contains("CATEGORY_CODE_EXISTS")
                .contains("BusinessException")
                .contains("categoryRepository.existsByCode(code)");
    }

    @Test
    void springDataMethodsBecomeQueriesOnTheEntityTable() {
        String exists = "java:method:" + CATEGORY_REPOSITORY + "#existsByCode(String)";
        IrNode query = node("java:query:" + exists + ":0");

        assertThat(query.name()).isEqualTo("SELECT category");
        assertThat(query.attrs()).containsEntry("queryKind", "DERIVED").containsEntry("operation", "READ");
        assertThat(edge(exists, query.key()).kind()).isEqualTo(EdgeKind.EXECUTES);
        IrEdge reads = edge(query.key(), "java:table:category");
        assertThat(reads.kind()).isEqualTo(EdgeKind.READS);
        assertThat(reads.confidence()).isEqualTo(Confidence.CERTAIN);

        // save() kế thừa từ JpaRepository: method tổng hợp, có truy vấn ghi
        String save = "java:method:" + CATEGORY_REPOSITORY + "#save(S)";
        assertThat(node(save).attrs()).containsEntry("synthetic", true).containsEntry("layer", "REPOSITORY");
        IrNode saveQuery = node("java:query:" + save + ":0");
        assertThat(saveQuery.name()).isEqualTo("INSERT/UPDATE category");
        assertThat(edge(saveQuery.key(), "java:table:category").kind()).isEqualTo(EdgeKind.WRITES);
    }

    @Test
    void explicitQueryAnnotationIsReadFromItsText() {
        IrNode query = node("java:query:java:method:" + CATEGORY_REPOSITORY + "#search(String):0");

        assertThat(query.attrs()).containsEntry("queryKind", "JPQL").containsEntry("verb", "SELECT");
        assertThat(query.attrs().get("text").toString()).startsWith("select c from Category c");
        assertThat(edge(query.key(), "java:table:category").confidence()).isEqualTo(Confidence.CERTAIN);

        IrNode nativeQuery = node("java:query:java:method:com.example.flow.product.ProductRepository#countByCategory(Long):0");
        assertThat(nativeQuery.attrs()).containsEntry("queryKind", "NATIVE").containsEntry("verb", "SELECT COUNT(*)");
        assertThat(edge(nativeQuery.key(), "java:table:product").kind()).isEqualTo(EdgeKind.READS);
    }

    @Test
    void interfaceWithTwoUnrankedImplementationsIsUnresolvedWithCandidates() {
        String delete = "java:method:" + CATEGORY_SERVICE + "#delete(Long)";
        IrEdge call = edgesFrom(delete, EdgeKind.CALLS).stream()
                .filter(e -> e.toKey().contains("ProductQueryService"))
                .findFirst()
                .orElseThrow();

        assertThat(call.confidence()).isEqualTo(Confidence.UNRESOLVED);
        assertThat(call.toKey()).isEqualTo("java:method:com.example.flow.product.ProductQueryService#countByCategoryId(Long)");
        assertThat(call.reason()).contains("2 lớp hiện thực").contains("@Primary");
        assertThat(call.candidates())
                .extracting(c -> simpleTarget(c.nodeKey()))
                .containsExactly(
                        "ProductServiceImpl#countByCategoryId(Long)", "ArchivedProductService#countByCategoryId(Long)");
        assertThat(call.candidates().get(1).reason()).contains("@ConditionalOnProperty(shop.product.archive)");
        // Ứng viên cũng được dựng tới tận bảng để người dùng chốt tay rồi dò tiếp được
        assertThat(node("java:query:java:method:com.example.flow.product.ProductRepository#countByCategory(Long):0"))
                .isNotNull();
    }

    @Test
    void primaryIsInferredAndQualifierIsCertain() {
        String confirm = "java:method:com.example.flow.order.OrderService#confirm(Long)";
        List<IrEdge> sends = edgesFrom(confirm, EdgeKind.CALLS).stream()
                .filter(e -> e.toKey().contains("Sender"))
                .toList();

        assertThat(sends).hasSize(2);
        IrEdge primary = sends.get(0);
        assertThat(primary.toKey()).contains("EmailSender#send(String)");
        assertThat(primary.confidence()).isEqualTo(Confidence.INFERRED);
        assertThat(primary.reason()).contains("@Primary");
        assertThat(primary.candidates()).hasSize(2);

        IrEdge qualified = sends.get(1);
        assertThat(qualified.toKey()).contains("SmsSender#send(String)");
        assertThat(qualified.confidence()).isEqualTo(Confidence.CERTAIN);
        assertThat(qualified.reason()).contains("@Qualifier(\"sms\")");
    }

    @Test
    void chainedCallsAndVarAreResolvedByDeclaredReturnTypes() {
        String confirm = "java:method:com.example.flow.order.OrderService#confirm(Long)";

        // var order = repo.findById(id).orElseThrow(); order.isPending(): Optional<Order> → Order → Order#isPending()
        IrEdge pending = edge(confirm, "java:method:com.example.flow.order.Order#isPending()");
        assertThat(pending.confidence()).isEqualTo(Confidence.CERTAIN);
        assertThat(node("java:method:com.example.flow.order.OrderRepository#findById(ID)").attrs())
                .containsEntry("returnType", "Optional<Order>");
    }

    @Test
    void serviceMethodNotFoundIsUnresolvedWithoutCandidatesAndSpringDataCrudNeverIs() {
        String confirm = "java:method:com.example.flow.order.OrderService#confirm(Long)";

        IrEdge record = edgesFrom(confirm, EdgeKind.CALLS).stream()
                .filter(e -> e.toKey().contains("AuditService#record"))
                .findFirst()
                .orElseThrow();
        assertThat(record.confidence()).isEqualTo(Confidence.UNRESOLVED);
        assertThat(record.candidates()).isEmpty();
        assertThat(record.reason()).contains("Không tìm thấy hàm record trong AuditService");
        assertThat(node(record.toKey()).attrs()).containsEntry("unresolvedMethod", true).containsEntry("layer", "SERVICE");

        // findById, save… là hàm CRUD của Spring Data: thao tác truy vấn CSDL (EXECUTES), không phải UNRESOLVED
        assertThat(fragment.edges())
                .filteredOn(e -> e.confidence() == Confidence.UNRESOLVED && e.toKey() != null)
                .noneMatch(e -> e.toKey().contains("Repository#"));
        assertThat(fragment.edges())
                .filteredOn(e -> e.kind() == EdgeKind.EXECUTES && e.fromKey().endsWith("#findById(ID)"))
                .isNotEmpty();
    }

    @Test
    void dynamicQueryReadsTablesFromFragmentsAndEntityNaming() {
        String search = "java:method:com.example.flow.product.ProductSearchDao#search(String,boolean)";
        IrNode query = node("java:query:" + search + ":0");

        assertThat(query.attrs()).containsEntry("queryKind", "DYNAMIC").containsEntry("verb", "SELECT");
        assertThat(query.name()).isEqualTo("SELECT product, product_brand");
        assertThat(edge(search, query.key()).confidence()).isEqualTo(Confidence.INFERRED);

        // Entity không khai @Table: tên bảng suy ra theo quy ước, nên chỉ INFERRED
        IrEdge brand = edge(query.key(), "java:table:product_brand");
        assertThat(brand.confidence()).isEqualTo(Confidence.INFERRED);
        assertThat(node("java:table:product_brand").attrs()).containsEntry("entity", "ProductBrand");
        assertThat(edge(query.key(), "java:table:product").kind()).isEqualTo(EdgeKind.READS);
    }

    @Test
    void tableNameCanComeFromAConstant() {
        assertThat(node("java:table:shop_order").attrs()).containsEntry("entity", "Order");
    }

    @Test
    void everyEdgeHasLocationOrReasonAndEndsOnExistingNode() {
        var keys = fragment.nodes().stream().map(IrNode::key).toList();

        for (IrEdge e : fragment.edges()) {
            assertThat(e.location() != null || e.reason() != null).as(e.toString()).isTrue();
            assertThat(keys).as("from " + e).contains(e.fromKey());
            if (e.toKey() != null) {
                assertThat(keys).as("to " + e).contains(e.toKey());
            }
            e.candidates().forEach(c -> assertThat(keys).contains(c.nodeKey()));
        }
    }

    // ---------------- tiện ích ----------------

    private List<IrNode> nodes(NodeKind kind) {
        return fragment.nodes().stream().filter(n -> n.kind() == kind).toList();
    }

    private IrNode node(String key) {
        return fragment.nodes().stream().filter(n -> n.key().equals(key)).findFirst().orElseThrow(() -> new AssertionError("Không có node " + key));
    }

    private List<IrEdge> edgesFrom(String key, EdgeKind kind) {
        return fragment.edges().stream().filter(e -> e.fromKey().equals(key) && e.kind() == kind).toList();
    }

    private IrEdge edge(String from, String to) {
        return fragment.edges().stream()
                .filter(e -> e.fromKey().equals(from) && to.equals(e.toKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không có cạnh " + from + " → " + to));
    }

    /** {@code java:method:a.b.Foo#bar(X)} → {@code Foo#bar(X)}. */
    private static String simpleTarget(String key) {
        String value = key.substring(key.indexOf("method:") + "method:".length());
        String type = value.substring(0, value.indexOf('#'));
        return type.substring(type.lastIndexOf('.') + 1) + value.substring(value.indexOf('#'));
    }
}
