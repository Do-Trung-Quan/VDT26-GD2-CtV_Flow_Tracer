package com.ctv.flowtracer.app.scan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một lần quét mã nguồn của Workspace. Bảng {@code scan_runs}. */
@Entity
@Table(name = "scan_runs")
public class ScanRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "scan_trigger")
    private ScanTrigger trigger;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "scan_status")
    private ScanStatus status;

    /** Số file theo loại thay đổi, số node/edge, số lỗi, thời gian từng giai đoạn. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> stats = new LinkedHashMap<>();

    @Column(name = "error_summary", columnDefinition = "text")
    private String errorSummary;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ScanRun() {}

    public ScanRun(UUID workspaceId, ScanTrigger trigger, Instant startedAt) {
        this.workspaceId = workspaceId;
        this.trigger = trigger;
        this.status = ScanStatus.PENDING;
        this.startedAt = startedAt;
    }

    /** Chuyển trạng thái; chặn chuyển sai (vd từ SUCCEEDED về RUNNING). */
    public void transitionTo(ScanStatus next, Instant at) {
        if (!status.allowedNext().contains(next)) {
            throw new IllegalStateException("Không thể chuyển lần quét từ " + status + " sang " + next);
        }
        this.status = next;
        if (next.isFinished()) {
            this.finishedAt = at;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public ScanTrigger getTrigger() {
        return trigger;
    }

    public ScanStatus getStatus() {
        return status;
    }

    public Map<String, Object> getStats() {
        return stats;
    }

    public void setStats(Map<String, Object> stats) {
        this.stats = stats == null ? new LinkedHashMap<>() : new LinkedHashMap<>(stats);
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
