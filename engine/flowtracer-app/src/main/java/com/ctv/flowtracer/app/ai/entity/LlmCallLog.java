package com.ctv.flowtracer.app.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Nhật ký mỗi lần gọi AI. KHÔNG lưu nội dung prompt — chỉ lưu hash. Bảng {@code llm_call_logs}. */
@Entity
@Table(name = "llm_call_logs")
public class LlmCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null với lời gọi hệ thống (vd kiểm tra kết nối) hoặc khi Workspace đã bị xóa. */
    @Column(name = "workspace_id")
    private UUID workspaceId;

    /** Tên AiPurpose, hoặc CONNECTION_TEST. */
    @Column(nullable = false, length = 30)
    private String purpose;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false, length = 50)
    private String model;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "prompt_hash", nullable = false, length = 64)
    private String promptHash;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "llm_call_status")
    private LlmCallStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LlmCallLog() {}

    public LlmCallLog(
            UUID workspaceId,
            String purpose,
            String provider,
            String model,
            String promptHash,
            Integer inputTokens,
            Integer outputTokens,
            Integer latencyMs,
            LlmCallStatus status) {
        this.workspaceId = workspaceId;
        this.purpose = purpose;
        this.provider = provider;
        this.model = model;
        this.promptHash = promptHash;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getPromptHash() {
        return promptHash;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public LlmCallStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
