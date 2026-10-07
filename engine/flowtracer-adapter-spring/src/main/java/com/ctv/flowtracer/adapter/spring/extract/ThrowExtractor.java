package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;

/** Câu lệnh throw: loại exception, khóa/chuỗi thông báo, guard. */
public final class ThrowExtractor {

    public List<JavaFileFacts.ThrowFact> extract(CompilationUnit unit) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
