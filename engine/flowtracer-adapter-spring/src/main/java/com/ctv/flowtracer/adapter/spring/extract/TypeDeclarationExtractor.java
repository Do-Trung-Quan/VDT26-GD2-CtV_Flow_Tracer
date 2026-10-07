package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;

/** package, import, class/interface/enum/record, extends/implements, annotation cấp class. */
public final class TypeDeclarationExtractor {

    public List<JavaFileFacts.TypeFact> extract(CompilationUnit unit) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
