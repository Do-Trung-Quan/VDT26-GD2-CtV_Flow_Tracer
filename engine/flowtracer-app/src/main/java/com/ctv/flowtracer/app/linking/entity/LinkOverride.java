package com.ctv.flowtracer.app.linking.entity;

import com.ctv.flowtracer.ir.EdgeKind;
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

/**
 * Quyết định chốt tay của người dùng về một liên kết. Trỏ tới node bằng KEY (không khóa ngoại) để giữ được qua các
 * lần quét. Bảng {@code link_overrides}.
 */
@Entity
@Table(name = "link_overrides")
public class LinkOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "from_node_key", nullable = false, columnDefinition = "text")
    private String fromNodeKey;

    @Column(name = "to_node_key", columnDefinition = "text")
    private String toNodeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "edge_kind", nullable = false, length = 30)
    private EdgeKind edgeKind;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "override_decision")
    private OverrideDecision decision;

    @Column(columnDefinition = "text")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LinkOverride() {}

    public LinkOverride(UUID workspaceId, String fromNodeKey, String toNodeKey, EdgeKind edgeKind, OverrideDecision decision, String note) {
        if (decision == OverrideDecision.PIN && toNodeKey == null) {
            throw new IllegalArgumentException("PIN cần toNodeKey");
        }
        this.workspaceId = workspaceId;
        this.fromNodeKey = fromNodeKey;
        this.toNodeKey = toNodeKey;
        this.edgeKind = edgeKind;
        this.decision = decision;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getFromNodeKey() {
        return fromNodeKey;
    }

    public String getToNodeKey() {
        return toNodeKey;
    }

    public EdgeKind getEdgeKind() {
        return edgeKind;
    }

    public OverrideDecision getDecision() {
        return decision;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
