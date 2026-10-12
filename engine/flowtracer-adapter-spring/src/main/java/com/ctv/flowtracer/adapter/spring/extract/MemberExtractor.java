package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Field (kiểu, final, annotation, hằng chuỗi), constructor, method (chữ ký, annotation, Javadoc). */
public final class MemberExtractor {

    /** Tên dùng cho constructor trong {@link JavaFileFacts.MethodFact#name()}. */
    public static final String CONSTRUCTOR = "<init>";

    public List<JavaFileFacts.MethodFact> extract(CompilationUnit unit) {
        var result = new ArrayList<JavaFileFacts.MethodFact>();
        unit.findAll(ConstructorDeclaration.class).forEach(c -> result.add(toFact(c, CONSTRUCTOR, null)));
        unit.findAll(MethodDeclaration.class)
                .forEach(m -> result.add(toFact(m, m.getNameAsString(), AstSupport.typeText(m.getType()))));
        return result;
    }

    public List<JavaFileFacts.FieldFact> extractFields(CompilationUnit unit) {
        Map<String, String> constants = constants(unit);
        var result = new ArrayList<JavaFileFacts.FieldFact>();
        for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
            String declaringType = AstSupport.enclosingTypeFqn(field).orElse("");
            field.getVariables().forEach(v -> result.add(new JavaFileFacts.FieldFact(
                    declaringType,
                    v.getNameAsString(),
                    AstSupport.typeText(v.getType()),
                    field.isFinal(),
                    AstSupport.annotationNames(field),
                    constants.get(declaringType + "." + v.getNameAsString()),
                    AstSupport.line(v),
                    field.isStatic(),
                    AstSupport.annotationValues(field))));
        }
        return result;
    }

    /**
     * Hằng chuỗi {@code static final} của các kiểu trong file: literal, nối literal, hoặc nối với hằng khác cùng kiểu
     * (vd {@code API_V1 + "/categories"}). Giải lặp tới khi không giải thêm được (tránh vòng tham chiếu).
     *
     * @return {@code FQN.TÊN} → giá trị
     */
    private static Map<String, String> constants(CompilationUnit unit) {
        Map<String, Expression> pending = new LinkedHashMap<>();
        for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
            if (field.isStatic() && field.isFinal()) {
                String type = AstSupport.enclosingTypeFqn(field).orElse("");
                field.getVariables().forEach(v -> v.getInitializer()
                        .ifPresent(init -> pending.put(type + "." + v.getNameAsString(), init)));
            }
        }
        Map<String, String> resolved = new TreeMap<>();
        boolean progress = true;
        while (progress && !pending.isEmpty()) {
            progress = false;
            for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                String type = e.getKey().substring(0, e.getKey().lastIndexOf('.'));
                Optional<String> value = AstSupport.stringValue(
                        e.getValue(), name -> Optional.ofNullable(resolved.get(type + "." + name)));
                if (value.isPresent()) {
                    resolved.put(e.getKey(), value.get());
                    it.remove();
                    progress = true;
                }
            }
        }
        return resolved;
    }

    /** Chữ ký dùng trong {@link JavaFileFacts.MappingFact#method()}: {@code tên(kiểu,...)} theo kiểu viết trong mã nguồn. */
    static String signature(CallableDeclaration<?> callable, String name) {
        return name + "(" + String.join(",", paramTypes(callable)) + ")";
    }

    private static JavaFileFacts.MethodFact toFact(CallableDeclaration<?> callable, String name, String returnType) {
        return new JavaFileFacts.MethodFact(
                AstSupport.enclosingTypeFqn(callable).orElse(""),
                name,
                paramTypes(callable),
                returnType,
                annotationsAndModifiers(callable),
                AstSupport.javadoc(callable),
                AstSupport.line(callable),
                AstSupport.endLine(callable),
                callable.getParameters().stream().map(Parameter::getNameAsString).toList(),
                callable.getParameters().stream().map(MemberExtractor::qualifier).toList(),
                AstSupport.annotationValues(callable),
                callable instanceof ConstructorDeclaration
                        || (callable instanceof MethodDeclaration m && m.getBody().isPresent()));
    }

    /** Giá trị {@code @Qualifier} của tham số, "" nếu không có. */
    private static String qualifier(Parameter parameter) {
        return AstSupport.annotationValues(parameter).getOrDefault("Qualifier.value", "");
    }

    private static List<String> paramTypes(CallableDeclaration<?> callable) {
        return callable.getParameters().stream().map(Parameter::getType).map(AstSupport::typeText).toList();
    }

    /** Annotation của method; thêm {@code private}/{@code static} để pha sau phân biệt hàm nội bộ. */
    private static List<String> annotationsAndModifiers(CallableDeclaration<?> callable) {
        var result = new ArrayList<>(AstSupport.annotationNames(callable));
        if (callable.hasModifier(Modifier.Keyword.PRIVATE)) {
            result.add("private");
        }
        if (callable.hasModifier(Modifier.Keyword.STATIC)) {
            result.add("static");
        }
        return result;
    }
}
