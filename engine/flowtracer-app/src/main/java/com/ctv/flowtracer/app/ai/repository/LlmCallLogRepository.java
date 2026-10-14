package com.ctv.flowtracer.app.ai.repository;

import com.ctv.flowtracer.app.ai.entity.LlmCallLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, UUID> {

    List<LlmCallLog> findByCreatedAtBetweenOrderByCreatedAt(Instant from, Instant to);

    /** Tổng token (vào + ra) của các lần gọi thành công từ thời điểm {@code from} — để tính hạn mức ngày. */
    @Query(value = """
            SELECT COALESCE(SUM(COALESCE(input_tokens, 0) + COALESCE(output_tokens, 0)), 0)
            FROM llm_call_logs WHERE created_at >= :from AND status = 'OK'
            """, nativeQuery = true)
    long tokensSince(@Param("from") Instant from);

    /**
     * Số lượt gọi provider đã được xử lý (thành công hoặc bị loại do đầu ra sai) từ thời điểm {@code from} — xấp xỉ số "yêu cầu" mà nhà
     * cung cấp đã tính vào hạn mức theo ngày.
     */
    @Query(value = """
            SELECT COUNT(*) FROM llm_call_logs
            WHERE created_at >= :from AND status IN ('OK', 'REJECTED_BY_VALIDATION')
            """, nativeQuery = true)
    long requestsSince(@Param("from") Instant from);

    /** Số liệu tổng hợp theo mục đích trong khoảng [from, to). */
    @Query(value = """
            SELECT purpose AS purpose,
                   COUNT(*) AS calls,
                   COALESCE(SUM(input_tokens), 0) AS inputTokens,
                   COALESCE(SUM(output_tokens), 0) AS outputTokens,
                   COUNT(*) FILTER (WHERE status <> 'OK') AS errors,
                   COALESCE(AVG(latency_ms) FILTER (WHERE status = 'OK'), 0) AS avgLatencyMs
            FROM llm_call_logs
            WHERE created_at >= :from AND created_at < :to
              AND (CAST(:workspaceId AS uuid) IS NULL OR workspace_id = CAST(:workspaceId AS uuid))
            GROUP BY purpose ORDER BY purpose
            """, nativeQuery = true)
    List<UsageRow> usage(@Param("from") Instant from, @Param("to") Instant to, @Param("workspaceId") UUID workspaceId);

    /** Một dòng thống kê theo mục đích. */
    interface UsageRow {
        String getPurpose();

        long getCalls();

        long getInputTokens();

        long getOutputTokens();

        long getErrors();

        double getAvgLatencyMs();
    }
}
