package com.ctv.flowtracer.app.workspace.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Một dự án đang được phân tích. Bảng {@code workspaces}. */
@Entity
@Table(name = "workspaces")
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private WorkspaceSettings settings = WorkspaceSettings.empty();

    /** Lần quét gần nhất — chỉ giữ id vì ScanRun thuộc package scan. */
    @Column(name = "last_scan_run_id")
    private UUID lastScanRunId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Workspace() {}

    public Workspace(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WorkspaceSettings getSettings() {
        return settings;
    }

    public void setSettings(WorkspaceSettings settings) {
        this.settings = settings == null ? WorkspaceSettings.empty() : settings;
    }

    public UUID getLastScanRunId() {
        return lastScanRunId;
    }

    public void setLastScanRunId(UUID lastScanRunId) {
        this.lastScanRunId = lastScanRunId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
