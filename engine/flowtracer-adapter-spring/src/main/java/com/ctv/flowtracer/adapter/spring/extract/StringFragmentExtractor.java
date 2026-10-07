package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;

/** Literal, text block, StringBuilder.append trong method — nguyên liệu phân tích truy vấn ghép động. */
public final class StringFragmentExtractor {

    public List<JavaFileFacts.StringFragmentFact> extract(CompilationUnit unit) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
