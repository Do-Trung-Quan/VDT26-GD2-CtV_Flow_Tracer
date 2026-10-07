package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;
import java.util.Map;

/** DATA_TYPE: tên trường JSON (theo Jackson naming), kiểu, ràng buộc. */
public final class DtoSchemaBuilder {

    public List<IrNode> buildDataTypes(List<JavaFileFacts> facts, String jsonNaming) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
