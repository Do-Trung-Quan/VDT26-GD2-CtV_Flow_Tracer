package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.MappingFact;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.ParamFact;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** @RequestMapping/@Get/@Post/@Put/@Delete/@PatchMapping; @PathVariable/@RequestParam/@RequestBody; @Operation/@Tag. */
public final class MappingExtractor {

    /** Annotation đánh dấu lớp xử lý request HTTP. */
    public static final List<String> CONTROLLER_ANNOTATIONS = List.of("RestController", "Controller");

    private static final Map<String, String> SHORTCUT_MAPPINGS = Map.of(
            "GetMapping", "GET",
            "PostMapping", "POST",
            "PutMapping", "PUT",
            "DeleteMapping", "DELETE",
            "PatchMapping", "PATCH");

    public List<JavaFileFacts.MappingFact> extract(CompilationUnit unit) {
        var result = new ArrayList<MappingFact>();
        for (TypeDeclaration<?> type : unit.findAll(TypeDeclaration.class).stream()
                .map(t -> (TypeDeclaration<?>) t)
                .toList()) {
            boolean controller = AstSupport.annotation(type, CONTROLLER_ANNOTATIONS.toArray(String[]::new)).isPresent();
            Optional<AnnotationExpr> classMapping = AstSupport.annotation(type, "RequestMapping");
            if (!controller && classMapping.isEmpty()) {
                continue;
            }
            result.add(classLevel(type, classMapping));
            for (MethodDeclaration method : type.getMethods()) {
                handlerMapping(type, method).ifPresent(result::add);
            }
        }
        return result;
    }

    private static MappingFact classLevel(TypeDeclaration<?> type, Optional<AnnotationExpr> mapping) {
        Map<String, String> openApi = new TreeMap<>();
        AstSupport.annotation(type, "Tag").ifPresent(tag -> {
            AstSupport.member(tag, "name", "value").flatMap(AstSupport::stringValue).ifPresent(n -> openApi.put("tag", n));
            AstSupport.member(tag, "description").flatMap(AstSupport::stringValue).ifPresent(d -> openApi.put("tagDescription", d));
        });
        return new MappingFact(
                AstSupport.fqn(type),
                null,
                mapping.map(MappingExtractor::paths).orElse(List.of()),
                mapping.map(MappingExtractor::requestMethods).orElse(List.of()),
                mapping.map(m -> strings(m, "consumes")).orElse(List.of()),
                mapping.map(m -> strings(m, "produces")).orElse(List.of()),
                List.of(),
                openApi,
                AstSupport.line(type));
    }

    private static Optional<MappingFact> handlerMapping(TypeDeclaration<?> type, MethodDeclaration method) {
        for (AnnotationExpr annotation : method.getAnnotations()) {
            String name = AstSupport.simpleName(annotation.getNameAsString());
            List<String> httpMethods;
            if (SHORTCUT_MAPPINGS.containsKey(name)) {
                httpMethods = List.of(SHORTCUT_MAPPINGS.get(name));
            } else if (name.equals("RequestMapping")) {
                httpMethods = requestMethods(annotation);
            } else {
                continue;
            }
            List<String> paths = paths(annotation);
            return Optional.of(new MappingFact(
                    AstSupport.fqn(type),
                    MemberExtractor.signature(method, method.getNameAsString()),
                    paths.isEmpty() ? List.of("") : paths,
                    httpMethods,
                    strings(annotation, "consumes"),
                    strings(annotation, "produces"),
                    method.getParameters().stream().map(MappingExtractor::param).toList(),
                    operation(method),
                    AstSupport.line(method)));
        }
        return Optional.empty();
    }

    private static List<String> paths(AnnotationExpr mapping) {
        return AstSupport.member(mapping, "path", "value")
                .map(e -> AstSupport.elements(e).stream().map(AstSupport::pathValue).toList())
                .orElse(List.of());
    }

    private static List<String> requestMethods(AnnotationExpr mapping) {
        return AstSupport.member(mapping, "method")
                .map(e -> AstSupport.elements(e).stream().map(AstSupport::lastName).toList())
                .orElse(List.of());
    }

    /** Giá trị chuỗi của thuộc tính (literal → nguyên văn; hằng số như {@code MediaType.APPLICATION_JSON_VALUE} → tên). */
    private static List<String> strings(AnnotationExpr annotation, String member) {
        return AstSupport.member(annotation, member)
                .map(e -> AstSupport.elements(e).stream().map(AstSupport::lastName).toList())
                .orElse(List.of());
    }

    /** {@code @Operation(summary, description, operationId, tags, deprecated)} và {@code @Deprecated}. */
    private static Map<String, String> operation(MethodDeclaration method) {
        Map<String, String> openApi = new TreeMap<>();
        AstSupport.annotation(method, "Operation").ifPresent(op -> {
            AstSupport.member(op, "summary").flatMap(AstSupport::stringValue).ifPresent(v -> openApi.put("summary", v));
            AstSupport.member(op, "description").flatMap(AstSupport::stringValue).ifPresent(v -> openApi.put("description", v));
            AstSupport.member(op, "operationId").flatMap(AstSupport::stringValue).ifPresent(v -> openApi.put("operationId", v));
            AstSupport.member(op, "tags")
                    .map(e -> AstSupport.elements(e).stream().flatMap(t -> AstSupport.stringValue(t).stream()).toList())
                    .filter(tags -> !tags.isEmpty())
                    .ifPresent(tags -> openApi.put("tag", tags.getFirst()));
            AstSupport.member(op, "deprecated")
                    .flatMap(AstSupport::booleanValue)
                    .filter(Boolean::booleanValue)
                    .ifPresent(v -> openApi.put("deprecated", "true"));
        });
        if (AstSupport.annotation(method, "Deprecated").isPresent()) {
            openApi.put("deprecated", "true");
        }
        return openApi;
    }

    private static ParamFact param(Parameter parameter) {
        String type = AstSupport.typeText(parameter.getType());
        String declaredName = parameter.getNameAsString();
        for (AnnotationExpr a : parameter.getAnnotations()) {
            switch (AstSupport.simpleName(a.getNameAsString())) {
                case "PathVariable" -> {
                    return new ParamFact(nameOf(a, declaredName), type, ParamFact.Source.PATH, required(a, true), null);
                }
                case "RequestParam" -> {
                    String defaultValue = AstSupport.member(a, "defaultValue").flatMap(AstSupport::stringValue).orElse(null);
                    return new ParamFact(
                            nameOf(a, declaredName), type, ParamFact.Source.QUERY, defaultValue == null && required(a, true), defaultValue);
                }
                case "RequestBody" -> {
                    return new ParamFact(declaredName, type, ParamFact.Source.BODY, required(a, true), null);
                }
                case "RequestHeader" -> {
                    return new ParamFact(nameOf(a, declaredName), type, ParamFact.Source.HEADER, required(a, true), null);
                }
                default -> {
                    // annotation khác (vd @Valid) — xét tiếp
                }
            }
        }
        return new ParamFact(declaredName, type, ParamFact.Source.OTHER, false, null);
    }

    private static String nameOf(AnnotationExpr annotation, String fallback) {
        return AstSupport.member(annotation, "name", "value").flatMap(AstSupport::stringValue).orElse(fallback);
    }

    private static boolean required(AnnotationExpr annotation, boolean defaultValue) {
        return AstSupport.member(annotation, "required").flatMap(AstSupport::booleanValue).orElse(defaultValue);
    }
}
