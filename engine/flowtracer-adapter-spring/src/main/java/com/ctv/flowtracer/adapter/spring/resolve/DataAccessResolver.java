package com.ctv.flowtracer.adapter.spring.resolve;

import com.ctv.flowtracer.adapter.spring.facts.JavaFileFacts;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.spi.ResolveContext;
import java.util.List;
import java.util.Map;

/** Spring Data: findBy/countBy/existsBy/save/saveAll/delete…, kế thừa JpaRepository → DATA_QUERY + READ/WRITE. */
public final class DataAccessResolver {

    public List<IrNode> resolveQueries(List<JavaFileFacts> facts, ClassIndex index) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
