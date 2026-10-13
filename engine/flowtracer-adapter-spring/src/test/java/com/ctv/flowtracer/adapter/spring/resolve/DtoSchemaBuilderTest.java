package com.ctv.flowtracer.adapter.spring.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctv.flowtracer.adapter.spring.SpringStackAdapter;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.IrFragment;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.FileFacts;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import com.ctv.flowtracer.ir.spi.SourceFile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** DATA_TYPE từ mã Java chạy trong bộ nhớ: tên JSON, generic, kế thừa, enum, ràng buộc, thông báo lỗi. */
class DtoSchemaBuilderTest {

    private static final String BASE = "api/src/main/java/com/x/";

    private static IrFragment analyze(Map<String, String> files) {
        var all = new TreeMap<>(files);
        var adapter = new SpringStackAdapter();
        List<String> paths = List.copyOf(all.keySet());
        var extractCtx = new ExtractContext("t", paths, p -> Optional.ofNullable(all.get(p)), null);
        List<FileFacts> facts = paths.stream()
                .filter(adapter::analyzes)
                .map(p -> adapter.extract(extractCtx, new SourceFile(p, all.get(p), sha256(all.get(p)))))
                .toList();
        return adapter.resolve(new ResolveContext("t", paths, p -> Optional.ofNullable(all.get(p)), null, null), facts);
    }

    private static String sha256(String content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static IrNode type(IrFragment f, String simpleName) {
        return f.nodes().stream()
                .filter(n -> n.kind() == NodeKind.DATA_TYPE && n.name().equals(simpleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không có kiểu " + simpleName));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fields(IrNode type) {
        return (List<Map<String, Object>>) type.attrs().get("fields");
    }

    private static Map<String, String> project() {
        var files = new TreeMap<String, String>();
        files.put(BASE + "ItemController.java", """
                package com.x;
                import org.springframework.web.bind.annotation.*;
                import java.util.List;
                @RestController @RequestMapping("/items")
                public class ItemController {
                    @PostMapping public ApiResult<ItemDto> create(@RequestBody ItemRequest request) { return null; }
                    @GetMapping public ApiResult<List<ItemDto>> list(@RequestParam String keyword, @RequestParam(defaultValue = "0") int page) { return null; }
                }
                """);
        files.put(BASE + "ApiResult.java", """
                package com.x;
                public class ApiResult<T> { private String message; private T data; }
                """);
        files.put(BASE + "BaseRequest.java", """
                package com.x;
                public class BaseRequest<K> { private K key; private String requestId; }
                """);
        files.put(BASE + "ItemRequest.java", """
                package com.x;
                import jakarta.validation.constraints.*;
                import com.fasterxml.jackson.annotation.JsonIgnore;
                import com.fasterxml.jackson.annotation.JsonProperty;
                public class ItemRequest extends BaseRequest<Long> {
                    @NotBlank @Size(min = 2, max = 50) private String itemCode;
                    @Min(1) @Max(100) private Integer quantity;
                    @Pattern(regexp = "^[A-Z]+$") private String group;
                    @Email private String ownerEmail;
                    @JsonProperty("unit_name") private String unit;
                    @JsonIgnore private String internal;
                    private ItemKind kind;
                    private static final String CONSTANT = "x";
                }
                """);
        files.put(BASE + "ItemKind.java", "package com.x;\npublic enum ItemKind { PHYSICAL, DIGITAL }\n");
        files.put(BASE + "ItemDto.java", "package com.x;\npublic record ItemDto(Long id, String itemCode, java.util.List<String> tags) {}\n");
        files.put("api/src/main/resources/application.yml", "spring:\n  jackson:\n    property-naming-strategy: SNAKE_CASE\n");
        return files;
    }

    @Test
    void buildsDataTypesWithJsonNamesRequiredAndConstraints() {
        IrFragment f = analyze(project());

        IrNode request = type(f, "ItemRequest");
        assertThat(request.attrs().get("jsonNaming")).isEqualTo("SNAKE_CASE");
        var byName = new TreeMap<String, Map<String, Object>>();
        fields(request).forEach(m -> byName.put((String) m.get("name"), m));
        // lớp cha đứng trước (như Jackson), tham số kiểu K = Long được thay; @JsonIgnore và static bị bỏ
        assertThat(fields(request).stream().map(m -> m.get("name"))).containsExactly(
                "key", "request_id", "item_code", "quantity", "group", "owner_email", "unit_name", "kind");
        assertThat(byName.get("key").get("type")).isEqualTo(Map.of("t", "integer"));
        assertThat(byName.get("item_code")).containsEntry("required", true)
                .containsEntry("constraints", List.of(Map.of("kind", "notBlank"), Map.of("kind", "size", "min", 2L, "max", 50L)));
        assertThat(byName.get("quantity").get("constraints")).isEqualTo(List.of(Map.of("kind", "min", "value", 1L), Map.of("kind", "max", "value", 100L)));
        assertThat(byName.get("group").get("constraints")).isEqualTo(List.of(Map.of("kind", "pattern", "value", "^[A-Z]+$")));
        assertThat(byName.get("owner_email").get("constraints")).isEqualTo(List.of(Map.of("kind", "email")));
        assertThat(byName.get("kind").get("type")).isEqualTo(Map.of("t", "enum", "values", List.of("PHYSICAL", "DIGITAL")));
    }

    @Test
    void recordComponentsAndGenericEnvelopeKeepTheirShape() {
        IrFragment f = analyze(project());

        assertThat(fields(type(f, "ItemDto")).stream().map(m -> m.get("name"))).containsExactly("id", "item_code", "tags");
        assertThat(fields(type(f, "ItemDto")).get(2).get("type")).isEqualTo(Map.of("t", "array", "items", Map.of("t", "string")));
        IrNode envelope = type(f, "ApiResult");
        assertThat(envelope.attrs().get("typeParameters")).isEqualTo(List.of("T"));
        assertThat(fields(envelope).get(1).get("type")).isEqualTo(Map.of("t", "var", "name", "T"));

        IrNode create = f.nodes().stream().filter(n -> n.key().equals("java:endpoint:POST /items")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) create.attrs().get("responseTypeExpr");
        assertThat(response.get("t")).isEqualTo("ref");
        assertThat(response.get("key")).isEqualTo("java:type:com.x.ApiResult");
        assertThat(response.get("args")).isEqualTo(List.of(Map.of("t", "ref", "key", "java:type:com.x.ItemDto")));
    }

    @Test
    void endpointParamsGetTypeExpressionsAndUsesTypeEdges() {
        IrFragment f = analyze(project());

        IrNode list = f.nodes().stream().filter(n -> n.key().equals("java:endpoint:GET /items")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var params = (List<Map<String, Object>>) list.attrs().get("params");
        assertThat(params).extracting(p -> p.get("name") + ":" + ((Map<?, ?>) p.get("typeExpr")).get("t")).containsExactly("keyword:string", "page:integer");
        assertThat(f.edges().stream().filter(e -> e.kind() == EdgeKind.USES_TYPE && e.fromKey().equals("java:endpoint:POST /items")))
                .extracting(e -> e.toKey())
                .containsExactlyInAnyOrder("java:type:com.x.ItemRequest", "java:type:com.x.ApiResult", "java:type:com.x.ItemDto");
    }

    @Test
    void throwMessagesComeFromTheVietnameseMessageFile() {
        var files = project();
        files.put(BASE + "ItemService.java", """
                package com.x;
                import org.springframework.stereotype.Service;
                @Service
                public class ItemService {
                    public void check(String code) {
                        if (code == null) { throw new IllegalStateException("ITEM_MISSING"); }
                        if (code.isEmpty()) { throw new IllegalStateException("ITEM_EMPTY"); }
                    }
                }
                """);
        files.put(BASE + "ItemController.java", files.get(BASE + "ItemController.java")
                .replace("public class ItemController {", "public class ItemController {\n    private final ItemService itemService = null;")
                .replace("return null; }\n    @GetMapping", "itemService.check(null); return null; }\n    @GetMapping"));
        files.put("api/src/main/resources/messages_vi.properties", "ITEM_MISSING=Thiếu mã mặt hàng\n");
        files.put("api/src/main/resources/messages.properties", "ITEM_MISSING=Item code missing\nITEM_EMPTY=Mã trống\n");

        IrFragment f = analyze(files);

        IrNode check = f.nodes().stream().filter(n -> n.key().contains("ItemService#check")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var thrown = (List<Map<String, Object>>) check.attrs().get("throws");
        assertThat(thrown).extracting(t -> t.get("messageKey") + "=" + t.get("message"))
                .containsExactly("ITEM_MISSING=Thiếu mã mặt hàng", "ITEM_EMPTY=Mã trống");
    }

    @Test
    void errorsRaisedFromAnEnumConstantResolveTheirMessageThroughTheEnumAndHyphenatedMessageFile() {
        var files = project();
        files.put(BASE + "AppError.java", """
                package com.x;
                public enum AppError {
                    ITEM_NOT_FOUND(2201, "msg.item.not.found", 404),
                    ITEM_CODE_REQUIRED(2202, "msg.item.code.required", 400);
                    AppError(int code, String messageKey, int status) {}
                    public RuntimeException exception() { return new RuntimeException(); }
                }
                """);
        files.put(BASE + "ItemService.java", """
                package com.x;
                import org.springframework.stereotype.Service;
                @Service
                public class ItemService {
                    public void check(String code) {
                        if (code == null) { throw AppError.ITEM_CODE_REQUIRED.exception(); }
                        if (code.isEmpty()) { throw AppError.ITEM_NOT_FOUND.exception(); }
                    }
                }
                """);
        files.put(BASE + "ItemController.java", files.get(BASE + "ItemController.java")
                .replace("public class ItemController {", "public class ItemController {\n    private final ItemService itemService = null;")
                .replace("return null; }\n    @GetMapping", "itemService.check(null); return null; }\n    @GetMapping"));
        files.put("api/src/main/resources/messages-app_vi.properties",
                "msg.item.code.required=Mã mặt hàng không được để trống\nmsg.item.not.found=Không tìm thấy mặt hàng\n");
        files.put("api/src/main/resources/messages-app.properties", "msg.item.code.required=Item code is required\n");

        IrFragment f = analyze(files);

        IrNode check = f.nodes().stream().filter(n -> n.key().contains("ItemService#check")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked")
        var thrown = (List<Map<String, Object>>) check.attrs().get("throws");
        assertThat(thrown).extracting(t -> t.get("messageKey") + "=" + t.get("message"))
                .containsExactly("ITEM_CODE_REQUIRED=Mã mặt hàng không được để trống", "ITEM_NOT_FOUND=Không tìm thấy mặt hàng");
    }

    @Test
    void namingStrategiesFollowJackson() {
        assertThat(DtoSchemaBuilder.applyNaming("itemCode", "SNAKE_CASE")).isEqualTo("item_code");
        assertThat(DtoSchemaBuilder.applyNaming("itemCode", "KEBAB_CASE")).isEqualTo("item-code");
        assertThat(DtoSchemaBuilder.applyNaming("itemCode", "UPPER_CAMEL_CASE")).isEqualTo("ItemCode");
        assertThat(DtoSchemaBuilder.applyNaming("itemCode", "LOWER_CASE")).isEqualTo("itemcode");
        assertThat(DtoSchemaBuilder.applyNaming("itemCode", null)).isEqualTo("itemCode");
        assertThat(DtoSchemaBuilder.applyNaming("itemCode", "org.springframework.…SnakeCaseStrategy")).isEqualTo("itemCode");
    }
}
