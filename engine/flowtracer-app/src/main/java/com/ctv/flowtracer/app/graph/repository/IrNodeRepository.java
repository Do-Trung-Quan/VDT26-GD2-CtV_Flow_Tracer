package com.ctv.flowtracer.app.graph.repository;

import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Truy cập bảng {@code ir_nodes} bằng JdbcClient (ghi theo lô 1.000 bản ghi). */
@Repository
public class IrNodeRepository {

    static final int BATCH_SIZE = 1_000;

    private final JdbcClient jdbc;

    public IrNodeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Upsert theo (workspace_id, key). */
    public void upsertAll(List<IrNodeRow> rows) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }

    public Optional<IrNodeRow> findByKey(UUID workspaceId, String key) {
        throw new UnsupportedOperationException("TODO: chưa triển khai");
    }

    public long countByWorkspace(UUID workspaceId) {
        return jdbc.sql("SELECT count(*) FROM ir_nodes WHERE workspace_id = :ws")
                .param("ws", workspaceId)
                .query(Long.class)
                .single();
    }
}
