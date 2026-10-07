package com.ctv.flowtracer.adapter.spring.extract;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.spi.ExtractContext;
import com.ctv.flowtracer.ir.spi.SourceFile;

/** PHA 1: parse một file (chịu lỗi cú pháp) bằng JavaParser + JavaSymbolSolver và gom dữ kiện. */
public final class JavaFileExtractor {

    public JavaFileFacts extract(ExtractContext ctx, SourceFile file) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
