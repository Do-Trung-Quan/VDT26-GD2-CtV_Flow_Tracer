package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;

/** Trường DTO/Entity: kiểu, @JsonProperty, @NotNull/@NotBlank/@Size/@Min/@Max/@Pattern/@Email, @Column. */
public final class DtoFieldExtractor {

    public List<JavaFileFacts.DtoFieldFact> extract(CompilationUnit unit) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
