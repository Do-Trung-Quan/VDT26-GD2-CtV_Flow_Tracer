package com.ctv.flowtracer.app.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * Cấu hình chung chỉnh được trên giao diện (vd {@code ai.purposes}). KHÔNG chứa API key hay lựa chọn provider —
 * những thứ đó chỉ nằm trong biến môi trường. Bảng {@code app_settings}.
 */
@Entity
@Table(name = "app_settings")
public class AppSetting {

    public static final String AI_PURPOSES = "ai.purposes";

    @Id
    @Column(name = "key", length = 100)
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> value;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppSetting() {}

    public AppSetting(String key, Map<String, Object> value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public Map<String, Object> getValue() {
        return value;
    }

    public void setValue(Map<String, Object> value) {
        this.value = value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
