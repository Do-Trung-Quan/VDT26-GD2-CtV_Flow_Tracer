package com.ctv.flowtracer.ir;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/** Vị trí trong mã nguồn: đường dẫn tương đối trong Source và khoảng dòng (đánh số từ 1). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceLocation(String filePath, Integer startLine, Integer endLine) {

    public SourceLocation {
        Objects.requireNonNull(filePath, "filePath");
    }

    public static SourceLocation of(String filePath, int line) {
        return new SourceLocation(filePath, line, line);
    }
}
