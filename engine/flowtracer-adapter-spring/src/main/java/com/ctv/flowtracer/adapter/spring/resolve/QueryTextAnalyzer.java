package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;
import java.util.Map;

/** JPQL/SQL (@Query, chuỗi ghép động): FROM/JOIN/UPDATE/INSERT/DELETE → entity/bảng; điều kiện phân loại. */
public final class QueryTextAnalyzer {

    public List<String> referencedTables(String queryText) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
