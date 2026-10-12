package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts.TypeFact;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.util.List;

/** package, import, class/interface/enum/record, extends/implements, annotation cấp class (kể cả kiểu lồng nhau). */
public final class TypeDeclarationExtractor {

    public List<JavaFileFacts.TypeFact> extract(CompilationUnit unit) {
        return unit.findAll(TypeDeclaration.class).stream()
                .map(t -> toFact((TypeDeclaration<?>) t))
                .toList();
    }

    private static TypeFact toFact(TypeDeclaration<?> type) {
        String superclass = null;
        List<String> interfaces = List.of();
        TypeFact.Kind kind;
        if (type instanceof ClassOrInterfaceDeclaration c) {
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
        } else if (type instanceof RecordDeclaration r) {
            kind = TypeFact.Kind.RECORD;
            interfaces = names(r.getImplementedTypes());
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
                type.hasModifier(Modifier.Keyword.ABSTRACT) ? List.of("abstract") : List.of());
    }

    private static List<String> names(List<ClassOrInterfaceType> types) {
        return types.stream().map(ClassOrInterfaceType::asString).toList();
    }
}
