package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;

/** Field (kiểu, final, @Autowired), constructor, method (chữ ký, annotation, Javadoc, @Transactional, @PreAuthorize). */
public final class MemberExtractor {

    public List<JavaFileFacts.MethodFact> extract(CompilationUnit unit) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
