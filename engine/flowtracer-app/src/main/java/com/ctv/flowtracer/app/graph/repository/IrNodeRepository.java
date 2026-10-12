package com.ctv.flowtracer.app.graph.repository;

import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Truy cập bảng {@code ir_nodes} bằng JdbcClient / JdbcTemplate (ghi theo lô {@value #BATCH_SIZE} bản ghi). */
@Repository
public class IrNodeRepository {

    static final int BATCH_SIZE = 1_000;

    private static final String SELECT = """
            SELECT id, workspace_id, source_id, file_id, key, kind, name, display_name, start_line, end_line, attrs,
                   (SELECT rel_path FROM source_files f WHERE f.id = n.file_id) AS file_path
            FROM ir_nodes n
            """;

    private static final String UPSERT = """
            INSERT INTO ir_nodes (workspace_id, key, kind, source_id, file_id, name, display_name, start_line, end_line, attrs)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (workspace_id, key) DO UPDATE SET
                kind = EXCLUDED.kind, source_id = EXCLUDED.source_id, file_id = EXCLUDED.file_id, name = EXCLUDED.name,
                display_name = EXCLUDED.display_name, start_line = EXCLUDED.start_line, end_line = EXCLUDED.end_line,
                attrs = EXCLUDED.attrs
            """;

    private static final TypeReference<Map<String, Object>> ATTRS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper json;

    public IrNodeRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate, JsonMapper json) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.json = json;
    }

    /** Upsert theo (workspace_id, key), giữ nguyên id của node đã có. */
    public void upsertAll(List<IrNodeRow> rows) {
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            List<Object[]> batch = new ArrayList<>();
            for (IrNodeRow row : rows.subList(from, Math.min(rows.size(), from + BATCH_SIZE))) {
                IrNode n = row.node();
                SourceLocation loc = n.location();
                batch.add(new Object[] {
                    row.workspaceId(),
                    n.key(),
                    n.kind().name(),
                    row.sourceId(),
                    row.fileId(),
                    n.name(),
                    n.displayName(),
                    loc == null ? null : loc.startLine(),
                    loc == null ? null : loc.endLine(),
                    json.writeValueAsString(n.attrs())
                });
            }
            jdbcTemplate.batchUpdate(UPSERT, batch, new int[] {
                Types.OTHER, Types.VARCHAR, Types.VARCHAR, Types.OTHER, Types.OTHER, Types.VARCHAR, Types.VARCHAR,
                Types.INTEGER, Types.INTEGER, Types.VARCHAR
            });
        }
    }

    /** Key của mọi node thuộc một Source. */
    public List<String> findKeysBySource(UUID sourceId) {
        return jdbc.sql("SELECT key FROM ir_nodes WHERE source_id = :source ORDER BY key")
                .param("source", sourceId)
                .query(String.class)
                .list();
    }

    /** Trong các key cho trước, những key đang thuộc Source KHÁC của cùng Workspace. */
    public List<String> findKeysOwnedByOtherSources(UUID workspaceId, UUID sourceId, Collection<String> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT key FROM ir_nodes WHERE workspace_id = :ws AND source_id <> :source AND key IN (:keys) ORDER BY key")
                .param("ws", workspaceId)
                .param("source", sourceId)
                .param("keys", keys)
                .query(String.class)
                .list();
    }

    /** Xóa các node của Source theo key (cạnh gắn với chúng bị xóa theo khóa ngoại). */
    public void deleteBySourceAndKeys(UUID sourceId, Collection<String> keys) {
        List<String> all = List.copyOf(keys);
        for (int from = 0; from < all.size(); from += BATCH_SIZE) {
            jdbc.sql("DELETE FROM ir_nodes WHERE source_id = :source AND key IN (:keys)")
                    .param("source", sourceId)
                    .param("keys", all.subList(from, Math.min(all.size(), from + BATCH_SIZE)))
                    .update();
        }
    }

    /** key → id của các node trong Workspace có key thuộc tập cho trước. */
    public Map<String, UUID> findIdsByKeys(UUID workspaceId, Collection<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        var result = new TreeMap<String, UUID>();
        jdbc.sql("SELECT key, id FROM ir_nodes WHERE workspace_id = :ws AND key IN (:keys)")
                .param("ws", workspaceId)
                .param("keys", keys)
                .query((rs, i) -> Map.entry(rs.getString("key"), rs.getObject("id", UUID.class)))
                .list()
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    /** Node theo loại, chỉ trong các Source cho trước, sắp theo key. */
    public List<IrNodeRow> findByKind(UUID workspaceId, NodeKind kind, Collection<UUID> sourceIds) {
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + " WHERE n.workspace_id = :ws AND n.kind = :kind AND n.source_id IN (:sources) ORDER BY n.key")
                .param("ws", workspaceId)
                .param("kind", kind.name())
                .param("sources", sourceIds)
                .query(this::mapRow)
                .list();
    }

    /** Node theo danh sách id (cùng Workspace), sắp theo key. */
    public List<IrNodeRow> findByIds(UUID workspaceId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + " WHERE n.workspace_id = :ws AND n.id IN (:ids) ORDER BY n.key")
                .param("ws", workspaceId)
                .param("ids", ids)
                .query(this::mapRow)
                .list();
    }

    /** Node theo danh sách key (cùng Workspace), sắp theo key. */
    public List<IrNodeRow> findByKeys(UUID workspaceId, Collection<String> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + " WHERE n.workspace_id = :ws AND n.key IN (:keys) ORDER BY n.key")
                .param("ws", workspaceId)
                .param("keys", keys)
                .query(this::mapRow)
                .list();
    }

    public Optional<IrNodeRow> findByKey(UUID workspaceId, String key) {
        return jdbc.sql(SELECT + " WHERE n.workspace_id = :ws AND n.key = :key")
                .param("ws", workspaceId)
                .param("key", key)
                .query(this::mapRow)
                .optional();
    }

    public long countByWorkspace(UUID workspaceId) {
        return jdbc.sql("SELECT count(*) FROM ir_nodes WHERE workspace_id = :ws")
                .param("ws", workspaceId)
                .query(Long.class)
                .single();
    }

    public long countByWorkspaceAndKind(UUID workspaceId, NodeKind kind) {
        return jdbc.sql("SELECT count(*) FROM ir_nodes WHERE workspace_id = :ws AND kind = :kind")
                .param("ws", workspaceId)
                .param("kind", kind.name())
                .query(Long.class)
                .single();
    }

    public long countBySource(UUID sourceId) {
        return jdbc.sql("SELECT count(*) FROM ir_nodes WHERE source_id = :source")
                .param("source", sourceId)
                .query(Long.class)
                .single();
    }

    public long countBySourceAndKind(UUID sourceId, NodeKind kind) {
        return jdbc.sql("SELECT count(*) FROM ir_nodes WHERE source_id = :source AND kind = :kind")
                .param("source", sourceId)
                .param("kind", kind.name())
                .query(Long.class)
                .single();
    }

    private IrNodeRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        String filePath = rs.getString("file_path");
        Integer start = (Integer) rs.getObject("start_line");
        Integer end = (Integer) rs.getObject("end_line");
        var node = new IrNode(
                rs.getString("key"),
                NodeKind.valueOf(rs.getString("kind")),
                rs.getString("name"),
                rs.getString("display_name"),
                filePath == null ? null : new SourceLocation(filePath, start, end),
                json.readValue(rs.getString("attrs"), ATTRS));
        return new IrNodeRow(
                rs.getObject("id", UUID.class),
                rs.getObject("workspace_id", UUID.class),
                rs.getObject("source_id", UUID.class),
                rs.getObject("file_id", UUID.class),
                node);
    }
}
