package com.ctv.flowtracer.core.linker;

import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.LinkRule;
import java.util.List;

/** CỐT LÕI: nối HTTP_CALL (FE) với API_ENDPOINT (BE), sinh edge HTTP_REQUEST kèm mức tin cậy và lý do. */
public final class Linker {

    private final LinkRuleEngine ruleEngine = new LinkRuleEngine();
    private final EndpointMatcher matcher = new EndpointMatcher();

    /**
     * Kết luận cho mỗi lời gọi: một kết quả tốt nhất + URL phân giải hoàn toàn → CERTAIN; có ký tự đại diện hoặc
     * phải áp luật heuristic → INFERRED; nhiều kết quả ngang điểm → edge có candidates; không khớp → UNRESOLVED.
     *
     * @return các edge HTTP_REQUEST, đã sắp xếp ổn định
     */
    public List<IrEdge> link(List<IrNode> httpCalls, List<IrNode> endpoints, List<LinkRule> rules) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }
}
