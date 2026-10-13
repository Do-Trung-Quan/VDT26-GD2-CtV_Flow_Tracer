package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.TypeFact;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** package, import, class/interface/enum/record, extends/implements, annotation cấp class (kể cả kiểu lồng nhau). */
public final class TypeDeclarationExtractor {

    private static final int MAX_KEY_LENGTH = 80;

    public List<JavaFileFacts.TypeFact> extract(CompilationUnit unit) {
        return unit.findAll(TypeDeclaration.class).stream()
                .map(t -> toFact((TypeDeclaration<?>) t))
                .toList();
    }

    private static TypeFact toFact(TypeDeclaration<?> type) {
        String superclass = null;
        List<String> interfaces = List.of();
        List<String> typeParameters = List.of();
        List<String> enumConstants = List.of();
        Map<String, String> enumMessageKeys = Map.of();
        TypeFact.Kind kind;
        if (type instanceof ClassOrInterfaceDeclaration c) {
            typeParameters = c.getTypeParameters().stream().map(p -> p.getNameAsString()).toList();
            kind = c.isInterface() ? TypeFact.Kind.INTERFACE : TypeFact.Kind.CLASS;
            if (c.isInterface()) {
                interfaces = names(c.getExtendedTypes());
            } else {
                superclass = c.getExtendedTypes().getFirst().map(ClassOrInterfaceType::asString).orElse(null);
                interfaces = names(c.getImplementedTypes());
            }
        } else if (type instanceof EnumDeclaration e) {
            kind = TypeFact.Kind.ENUM;
            interfaces = names(e.getImplementedTypes());
            enumConstants = e.getEntries().stream().map(c -> c.getNameAsString()).toList();
            enumMessageKeys = enumMessageKeys(e);
        } else if (type instanceof RecordDeclaration r) {
            kind = TypeFact.Kind.RECORD;
            interfaces = names(r.getImplementedTypes());
            typeParameters = r.getTypeParameters().stream().map(p -> p.getNameAsString()).toList();
        } else if (type instanceof AnnotationDeclaration) {
            kind = TypeFact.Kind.ANNOTATION;
        } else {
            kind = TypeFact.Kind.CLASS;
        }
        return new TypeFact(
                AstSupport.fqn(type),
                kind,
                AstSupport.annotationNames(type),
                superclass,
                interfaces,
                AstSupport.javadoc(type),
                AstSupport.line(type),
                AstSupport.annotationValues(type),
                type.hasModifier(Modifier.Keyword.ABSTRACT) ? List.of("abstract") : List.of(),
                typeParameters,
                enumConstants,
                enumMessageKeys);
    }

    /** Hằng enum → đối số chuỗi đầu tiên của nó (khóa thông báo của enum mã lỗi); chỉ lấy chuỗi literal ngắn. */
    private static Map<String, String> enumMessageKeys(EnumDeclaration e) {
        Map<String, String> keys = new TreeMap<>();
        for (EnumConstantDeclaration c : e.getEntries()) {
            c.getArguments().stream()
                    .filter(a -> a instanceof StringLiteralExpr)
                    .map(a -> ((StringLiteralExpr) a).asString())
                    .filter(s -> !s.isBlank() && s.length() <= MAX_KEY_LENGTH)
                    .findFirst()
                    .ifPresent(s -> keys.put(c.getNameAsString(), s));
        }
        return keys;
    }

    private static List<String> names(List<ClassOrInterfaceType> types) {
        return types.stream().map(ClassOrInterfaceType::asString).toList();
    }
}
