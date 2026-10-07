package com.ctv.flowtracer.app.feature.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

/** (Mở rộng) Thành phần thuộc một nhóm chức năng, trỏ bằng node KEY. Bảng {@code feature_members}. */
@Entity
@Table(name = "feature_members")
@IdClass(FeatureMember.Id.class)
public class FeatureMember {

    /** Khóa chính kép (feature_id, node_key). */
    public record Id(UUID featureId, String nodeKey) implements Serializable {}

    @jakarta.persistence.Id
    @Column(name = "feature_id")
    private UUID featureId;

    @jakarta.persistence.Id
    @Column(name = "node_key", columnDefinition = "text")
    private String nodeKey;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    protected FeatureMember() {}

    public FeatureMember(UUID featureId, String nodeKey, int orderIndex) {
        this.featureId = featureId;
        this.nodeKey = nodeKey;
        this.orderIndex = orderIndex;
    }

    public UUID getFeatureId() {
        return featureId;
    }

    public String getNodeKey() {
        return nodeKey;
    }

    public int getOrderIndex() {
        return orderIndex;
    }
}
