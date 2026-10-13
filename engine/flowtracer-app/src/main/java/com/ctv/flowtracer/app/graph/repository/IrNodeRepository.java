package com.ctv.flowtracer.app.graph.repository;

import com.ctv.flowtracer.app.graph.model.IrNodeRow;
import com.ctv.flowtracer.app.graph.model.SearchCategory;
import com.ctv.flowtracer.app.graph.model.SearchHitRow;
import com.ctv.flowtracer.ir.IrNode;
import com.ctv.flowtracer.ir.NodeKind;
import com.ctv.flowtracer.ir.SourceLocation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
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

    /** Mọi node một loại của Workspace, sắp theo key. */
    public List<IrNodeRow> findAllByKind(UUID workspaceId, NodeKind kind) {
        return jdbc.sql(SELECT + " WHERE n.workspace_id = :ws AND n.kind = :kind ORDER BY n.key")
                .param("ws", workspaceId)
                .param("kind", kind.name())
                .query(this::mapRow)
                .list();
    }

    /** Xóa mọi node một loại của Workspace (edge nối tới chúng bị xóa theo khóa ngoại). */
    public int deleteByWorkspaceAndKind(UUID workspaceId, NodeKind kind) {
        return jdbc.sql("DELETE FROM ir_nodes WHERE workspace_id = :ws AND kind = :kind")
                .param("ws", workspaceId)
                .param("kind", kind.name())
                .update();
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

    /** Node thuộc một trong các loại, chỉ trong các Source cho trước, sắp theo key. */
    public List<IrNodeRow> findByKinds(UUID workspaceId, Collection<NodeKind> kinds, Collection<UUID> sourceIds) {
        if (sourceIds.isEmpty() || kinds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + " WHERE n.workspace_id = :ws AND n.kind IN (:kinds) AND n.source_id IN (:sources) ORDER BY n.key")
                .param("ws", workspaceId)
                .param("kinds", kinds.stream().map(NodeKind::name).toList())
                .param("sources", sourceIds)
                .query(this::mapRow)
                .list();
    }

    /**
     * Tìm kiếm nhanh: mỗi từ khóa phải khớp tên, nhãn hiển thị hoặc key (chuỗi con, không phân biệt hoa thường) hoặc khớp
     * tiền tố một từ trong chỉ mục toàn văn {@code search_text}. Kết quả xếp theo độ khớp (khớp đúng, bắt đầu bằng, chứa),
     * giữ tối đa {@code limitPerCategory} kết quả mỗi nhóm kèm tổng số khớp của nhóm.
     */
    public List<SearchHitRow> search(
            UUID workspaceId, List<String> tokens, Collection<SearchCategory> categories, int limitPerCategory) {
        if (tokens.isEmpty() || categories.isEmpty()) {
            return List.of();
        }
        String phrase = String.join(" ", tokens).toLowerCase(Locale.ROOT);
        var where = new StringBuilder();
        var params = new LinkedHashMap<String, Object>();
        for (int i = 0; i < tokens.size(); i++) {
            where.append(" AND (n.name ILIKE :t").append(i).append(" OR n.display_name ILIKE :t").append(i)
                    .append(" OR n.key ILIKE :t").append(i);
            params.put("t" + i, "%" + escapeLike(tokens.get(i)) + "%");
            String prefixQuery = tsPrefix(tokens.get(i));
            if (prefixQuery != null) {
                where.append(" OR n.search_text @@ to_tsquery('simple', :q").append(i).append(")");
                params.put("q" + i, prefixQuery);
            }
            where.append(")");
        }
        String sql = """
                SELECT id, workspace_id, source_id, file_id, key, kind, name, display_name, start_line, end_line, attrs, file_path, total
                FROM (
                    SELECT s.*, COUNT(*) OVER (PARTITION BY s.cat) AS total,
                           ROW_NUMBER() OVER (PARTITION BY s.cat ORDER BY s.rank, s.key) AS rn
                    FROM (
                        SELECT n.id, n.workspace_id, n.source_id, n.file_id, n.key, n.kind, n.name, n.display_name,
                               n.start_line, n.end_line, n.attrs,
                               (SELECT rel_path FROM source_files f WHERE f.id = n.file_id) AS file_path,
                               %s AS cat,
                               CASE
                                   WHEN lower(coalesce(n.display_name, n.name)) = :phrase OR lower(n.name) = :phrase THEN 0
                                   WHEN lower(coalesce(n.display_name, n.name)) LIKE :phrasePrefix
                                        OR lower(n.name) LIKE :phrasePrefix THEN 1
                                   ELSE 2
                               END AS rank
                        FROM ir_nodes n
                        WHERE n.workspace_id = :ws AND n.kind IN (:kinds) %s
                    ) s
                ) t
                WHERE rn <= :limit
                ORDER BY cat, rn
                """.formatted(categoryExpression(), where);
        var spec = jdbc.sql(sql)
                .param("ws", workspaceId)
                .param("kinds", categories.stream().flatMap(c -> c.kinds().stream()).map(NodeKind::name).toList())
                .param("phrase", phrase)
                .param("phrasePrefix", escapeLike(phrase) + "%")
                .param("limit", limitPerCategory);
        for (var e : params.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        return spec.query((rs, i) -> new SearchHitRow(mapRow(rs, i), rs.getLong("total"))).list();
    }

    /** {@code CASE} gán mỗi loại node vào nhóm tìm kiếm của nó (chỉ gồm hằng số của enum, không có dữ liệu người dùng). */
    private static String categoryExpression() {
        var sb = new StringBuilder("CASE");
        for (SearchCategory c : SearchCategory.values()) {
            String kinds = c.kinds().stream().map(k -> "'" + k.name() + "'").collect(Collectors.joining(", "));
            sb.append(" WHEN n.kind IN (").append(kinds).append(") THEN '").append(c.name()).append("'");
        }
        return sb.append(" END").toString();
    }

    /** Thoát ký tự đặc biệt của LIKE (mặc định dấu thoát của PostgreSQL là {@code \}). */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Từ khóa → truy vấn tiền tố {@code từ:*} cho tsvector kiểu 'simple'; null nếu không còn ký tự chữ/số nào. */
    private static String tsPrefix(String token) {
        String cleaned = token.replaceAll("[^\\p{L}\\p{N}]", "");
        return cleaned.isEmpty() ? null : cleaned + ":*";
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
