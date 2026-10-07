package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;

/** Lời gọi trong thân method: receiver, tên, số tham số, callOrder, guard (if/else/try). */
public final class CallSiteExtractor {

    public List<JavaFileFacts.CallSiteFact> extract(CompilationUnit unit) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
