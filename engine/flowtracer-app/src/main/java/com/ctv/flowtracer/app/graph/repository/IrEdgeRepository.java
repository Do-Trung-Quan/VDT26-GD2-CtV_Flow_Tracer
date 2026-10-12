package com.ctv.flowtracer.app.graph.repository;

import com.ctv.flowtracer.app.graph.model.IrEdgeRow;
import com.ctv.flowtracer.ir.Candidate;
import com.ctv.flowtracer.ir.Confidence;
import com.ctv.flowtracer.ir.EdgeKind;
import com.ctv.flowtracer.ir.Guard;
import com.ctv.flowtracer.ir.IrEdge;
import com.ctv.flowtracer.ir.SourceLocation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
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

    private static final TypeReference<List<Candidate>> CANDIDATES = new TypeReference<>() {};

    /** Edge kèm key hai đầu và đường dẫn file (join); cột confidence ép về text để đọc được enum PostgreSQL. */
    private static final String SELECT = """
            SELECT e.id, e.workspace_id, e.from_node_id, e.to_node_id, e.file_id, e.kind, e.confidence::text AS confidence,
                   e.reason, e.call_order, e.guard, e.candidates, e.line,
                   f.key AS from_key, t.key AS to_key, sf.rel_path AS file_path
            FROM ir_edges e
            JOIN ir_nodes f ON f.id = e.from_node_id
            LEFT JOIN ir_nodes t ON t.id = e.to_node_id
            LEFT JOIN source_files sf ON sf.id = e.file_id
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

    /**
     * Id của node xuất phát và mọi node đi tới được qua {@code to_node_id} trong tối đa {@code maxDepth} bước. Ứng viên của
     * edge chưa xác định (nằm trong cột {@code candidates}) KHÔNG được đi theo — chỉ liên kết chốt tay mới đưa chúng vào luồng.
     */
    public List<UUID> reachableNodeIds(UUID startNodeId, int maxDepth) {
        return jdbc.sql("""
                        WITH RECURSIVE reach(id, depth) AS (
                            SELECT CAST(:start AS uuid), 0
                            UNION
                            SELECT e.to_node_id, r.depth + 1
                            FROM ir_edges e JOIN reach r ON e.from_node_id = r.id
                            WHERE e.to_node_id IS NOT NULL AND r.depth < :maxDepth
                        )
                        SELECT DISTINCT id FROM reach ORDER BY id
                        """)
                .param("start", startNodeId)
                .param("maxDepth", maxDepth)
                .query(UUID.class)
                .list();
    }

    /** Mọi edge xuất phát từ các node cho trước, sắp theo (node đi, thứ tự gọi) để kết quả ổn định. */
    public List<IrEdgeRow> findByFromNodes(UUID workspaceId, Collection<UUID> fromNodeIds) {
        if (fromNodeIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + " WHERE e.workspace_id = :ws AND e.from_node_id IN (:from) ORDER BY f.key, e.call_order NULLS LAST, e.kind, t.key")
                .param("ws", workspaceId)
                .param("from", fromNodeIds)
                .query(this::mapRow)
                .list();
    }

    private IrEdgeRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        String filePath = rs.getString("file_path");
        Integer line = (Integer) rs.getObject("line");
        String guard = rs.getString("guard");
        String candidates = rs.getString("candidates");
        String reason = rs.getString("reason");
        if (reason == null && filePath == null) {
            reason = "Không có vị trí mã nguồn"; // edge luôn phải có vị trí hoặc lý do
        }
        var edge = new IrEdge(
                rs.getString("from_key"),
                rs.getString("to_key"),
                EdgeKind.valueOf(rs.getString("kind")),
                Confidence.valueOf(rs.getString("confidence")),
                reason,
                (Integer) rs.getObject("call_order"),
                guard == null ? null : json.readValue(guard, Guard.class),
                candidates == null ? List.of() : json.readValue(candidates, CANDIDATES),
                filePath == null ? null : new SourceLocation(filePath, line, line));
        return new IrEdgeRow(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("from_node_id", UUID.class),
                rs.getObject("to_node_id", UUID.class),
                rs.getObject("file_id", UUID.class),
                edge);
    }

    public long countByWorkspace(UUID workspaceId) {
        return jdbc.sql("SELECT count(*) FROM ir_edges WHERE workspace_id = :ws")
                .param("ws", workspaceId)
                .query(Long.class)
                .single();
    }
}
