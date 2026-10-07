package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;
import java.util.Map;

/** Entity → bảng (@Table(name) hoặc tên class) → DB_TABLE. */
public final class EntityTableResolver {

    public Map<String, String> entityToTable(List<JavaFileFacts> facts) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
