package com.ctv.flowtracer.app.graph.repository;

import com.ctv.flowtracer.app.graph.model.IrEdgeRow;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Truy cập bảng {@code ir_edges} bằng JdbcClient. Cột confidence là enum PostgreSQL: dùng {@code ?::edge_confidence}. */
@Repository
public class IrEdgeRepository {

    private final JdbcClient jdbc;

    public IrEdgeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Thay toàn bộ edge xuất phát từ node thuộc một Source. */
    public void replaceForSource(UUID sourceId, List<IrEdgeRow> rows) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }

    public List<IrEdgeRow> findOutgoing(UUID fromNodeId) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }

    public long countByWorkspace(UUID workspaceId) {
        return jdbc.sql("SELECT count(*) FROM ir_edges WHERE workspace_id = :ws")
                .param("ws", workspaceId)
                .query(Long.class)
                .single();
    }
}
