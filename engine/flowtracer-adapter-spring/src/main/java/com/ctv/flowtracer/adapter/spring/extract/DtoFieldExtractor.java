package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Trường của DTO / Entity: field của lớp (không {@code static}, không {@code @JsonIgnore}) và thành phần của record, kèm kiểu
 * viết trong mã, {@code @JsonProperty}, ràng buộc Bean Validation ({@code @NotNull}, {@code @NotBlank}, {@code @Size},
 * {@code @Min}, {@code @Max}, {@code @Pattern}, {@code @Email}…) và {@code @Column}. Chỉ ghi dữ kiện của chính file; lớp nào là
 * DTO của API nào do pha 2 quyết định (bắt đầu từ kiểu của endpoint), nên ở đây ghi cho mọi lớp / record.
 */
public final class DtoFieldExtractor {

    /** Annotation đáng ghi lại (Bean Validation + JPA {@code @Column}); tên ngắn. */
    private static final Set<String> CONSTRAINTS = Set.of(
            "NotNull", "NotBlank", "NotEmpty", "Size", "Min", "Max", "DecimalMin", "DecimalMax", "Pattern", "Email", "Positive",
            "PositiveOrZero", "Negative", "NegativeOrZero", "Past", "PastOrPresent", "Future", "FutureOrPresent", "Digits",
            "AssertTrue", "AssertFalse", "Column", "Length", "Range");

    public List<JavaFileFacts.DtoFieldFact> extract(CompilationUnit unit) {
        var result = new ArrayList<JavaFileFacts.DtoFieldFact>();
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class).stream().map(t -> (TypeDeclaration<?>) t).toList()) {
            String owner = AstSupport.fqn(type);
            if (type instanceof RecordDeclaration record) {
                record.getParameters().forEach(p -> result.add(fromParameter(owner, p)));
            } else if (type instanceof ClassOrInterfaceDeclaration c && c.isInterface()) {
                continue;
            }
            for (FieldDeclaration field : type.getFields()) {
                if (field.isStatic() || AstSupport.annotation(field, "JsonIgnore").isPresent()) {
                    continue;
                }
                field.getVariables().forEach(v -> result.add(new JavaFileFacts.DtoFieldFact(
                        owner,
                        v.getNameAsString(),
                        jsonName(field),
                        AstSupport.typeText(v.getType()),
                        constraints(field),
                        AstSupport.line(v),
                        description(field))));
            }
        }
        return result;
    }

    private static JavaFileFacts.DtoFieldFact fromParameter(String owner, Parameter p) {
        return new JavaFileFacts.DtoFieldFact(
                owner, p.getNameAsString(), jsonName(p), AstSupport.typeText(p.getType()), constraints(p), AstSupport.line(p), description(p));
    }

    private static String jsonName(NodeWithAnnotations<?> node) {
        return AstSupport.annotation(node, "JsonProperty")
                .flatMap(a -> AstSupport.member(a, "value"))
                .flatMap(AstSupport::stringValue)
                .filter(s -> !s.isBlank())
                .orElse(null);
    }

    /** {@code Tên} hoặc {@code Tên(khóa=giá trị, …)} cho từng annotation ràng buộc, theo thứ tự khai báo. */
    private static List<String> constraints(NodeWithAnnotations<?> node) {
        Map<String, String> values = AstSupport.annotationValues(node);
        var result = new ArrayList<String>();
        for (AnnotationExpr a : node.getAnnotations()) {
            String name = AstSupport.simpleName(a.getNameAsString());
            if (!CONSTRAINTS.contains(name)) {
                continue;
            }
            Map<String, String> args = new TreeMap<>();
            values.forEach((k, v) -> {
                if (k.startsWith(name + ".")) {
                    args.put(k.substring(name.length() + 1), v);
                }
            });
            // @Size(max = MAX_CODE): giá trị là tên hằng số — giữ nguyên dạng tham chiếu, bỏ qua khi tính ràng buộc
            result.add(args.isEmpty() ? name : name + "(" + args.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).reduce((x, y) -> x + ", " + y).orElse("") + ")");
        }
        return result;
    }

    /** {@code @Schema(description)} (Swagger) rồi Javadoc của trường. */
    private static String description(NodeWithAnnotations<?> node) {
        Optional<String> schema = AstSupport.annotation(node, "Schema")
                .flatMap(a -> AstSupport.member(a, "description"))
                .flatMap(AstSupport::stringValue)
                .filter(s -> !s.isBlank());
        if (schema.isPresent()) {
            return schema.get();
        }
        if (node instanceof FieldDeclaration f) {
            String doc = AstSupport.javadoc(f);
            if (doc != null) {
                int dot = doc.indexOf(". ");
                String first = (dot < 0 ? doc : doc.substring(0, dot + 1)).replaceAll("\\s+", " ").strip();
                return first.isEmpty() ? null : first;
            }
        }
        return null;
    }
}
