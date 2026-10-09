package com.ctv.flowtracer.app.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bộ nhớ đệm nội dung do AI sinh, khóa theo mã băm đầu vào. Bảng {@code ai_artifacts}. */
@Entity
@Table(
        name = "ai_artifacts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "purpose", "target_key", "input_hash"}))
public class AiArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "target_key", nullable = false, columnDefinition = "text")
    private String targetKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "ai_purpose")
    private AiPurpose purpose;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "input_hash", nullable = false, length = 64)
    private String inputHash;

    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 50)
    private String model;

    /** Kết quả AI ĐÃ được kiểm tra hợp lệ. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Số lần bản ghi này được dùng lại thay vì gọi AI (cho thống kê tỷ lệ trúng cache). */
    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "last_hit_at")
    private Instant lastHitAt;

    protected AiArtifact() {}

    public AiArtifact(
            UUID workspaceId,
            String targetKey,
            AiPurpose purpose,
            String inputHash,
            String promptVersion,
            String provider,
            String model,
            Map<String, Object> content) {
        this.workspaceId = workspaceId;
        this.targetKey = targetKey;
        this.purpose = purpose;
        this.inputHash = inputHash;
        this.promptVersion = promptVersion;
        this.provider = provider;
        this.model = model;
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public AiPurpose getPurpose() {
        return purpose;
    }

    public String getInputHash() {
        return inputHash;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getHitCount() {
        return hitCount;
    }

    public Instant getLastHitAt() {
        return lastHitAt;
    }

    /** Ghi nhận một lần dùng lại nội dung đã lưu. */
    public void recordHit(Instant at) {
        this.hitCount++;
        this.lastHitAt = at;
    }
}
