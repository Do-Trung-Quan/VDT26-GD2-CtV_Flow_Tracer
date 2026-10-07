package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.github.javaparser.ast.CompilationUnit;
import java.util.List;

/** @RequestMapping/@Get/@Post/@Put/@Delete/@PatchMapping; @PathVariable/@RequestParam/@RequestBody; @Operation/@Tag. */
public final class MappingExtractor {

    public List<JavaFileFacts.MappingFact> extract(CompilationUnit unit) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
