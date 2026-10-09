package com.ctv.flowtracer.app.graph.repository;

import com.ctv.flowtracer.app.graph.model.IrEdgeRow;
import com.ctv.flowtracer.ir.IrEdge;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** Truy cập bảng {@code ir_edges} bằng JdbcClient / JdbcTemplate. Cột confidence là enum PostgreSQL: dùng {@code ?::edge_confidence}. */
@Repository
public class IrEdgeRepository {

    private static final int BATCH_SIZE = 1_000;

    private static final String INSERT = """
            INSERT INTO ir_edges (workspace_id, from_node_id, to_node_id, kind, confidence, reason, call_order, guard,
                                  candidates, file_id, line)
            VALUES (?, ?, ?, ?, ?::edge_confidence, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
            """;

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper json;

    public IrEdgeRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate, JsonMapper json) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.json = json;
    }

    /** Thay toàn bộ edge xuất phát từ node thuộc một Source. */
    public void replaceForSource(UUID sourceId, List<IrEdgeRow> rows) {
        jdbc.sql("DELETE FROM ir_edges WHERE from_node_id IN (SELECT id FROM ir_nodes WHERE source_id = :source)")
                .param("source", sourceId)
                .update();
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            List<Object[]> batch = new ArrayList<>();
            for (IrEdgeRow row : rows.subList(from, Math.min(rows.size(), from + BATCH_SIZE))) {
                IrEdge e = row.edge();
                batch.add(new Object[] {
                    row.workspaceId(),
                    row.fromNodeId(),
                    row.toNodeId(),
                    e.kind().name(),
                    e.confidence().name(),
                    e.reason(),
                    e.callOrder(),
                    e.guard() == null ? null : json.writeValueAsString(e.guard()),
                    e.candidates().isEmpty() ? null : json.writeValueAsString(e.candidates()),
                    row.fileId(),
                    e.location() == null ? null : e.location().startLine()
                });
            }
            jdbcTemplate.batchUpdate(INSERT, batch, new int[] {
                Types.OTHER, Types.OTHER, Types.OTHER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER,
                Types.VARCHAR, Types.VARCHAR, Types.OTHER, Types.INTEGER
            });
        }
    }

    public long countByWorkspace(UUID workspaceId) {
        return jdbc.sql("SELECT count(*) FROM ir_edges WHERE workspace_id = :ws")
                .param("ws", workspaceId)
                .query(Long.class)
                .single();
    }
}
