package com.ctv.flowtracer.app.workspace.entity;

import com.ctv.flowtracer.ir.SourceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/** Một thư mục mã nguồn thuộc Workspace, kèm vai trò. Bảng {@code sources}. */
@Entity
@Table(name = "sources")
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "source_role")
    private SourceRole role;

    /** Đường dẫn tương đối trong source-root. */
    @Column(name = "root_path", nullable = false, columnDefinition = "text")
    private String rootPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "include_globs", nullable = false)
    private List<String> includeGlobs = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exclude_globs", nullable = false)
    private List<String> excludeGlobs = List.of();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_source_id")
    private Source parent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_stacks", nullable = false)
    private List<DetectedStack> detectedStacks = List.of();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Source() {}

    public Source(Workspace workspace, String name, SourceRole role, String rootPath) {
        this.workspace = workspace;
        this.name = name;
        this.role = role;
        this.rootPath = rootPath;
    }

    public UUID getId() {
        return id;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SourceRole getRole() {
        return role;
    }

    public void setRole(SourceRole role) {
        this.role = role;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public List<String> getIncludeGlobs() {
        return includeGlobs;
    }

    public void setIncludeGlobs(List<String> includeGlobs) {
        this.includeGlobs = includeGlobs == null ? List.of() : List.copyOf(includeGlobs);
    }

    public List<String> getExcludeGlobs() {
        return excludeGlobs;
    }

    public void setExcludeGlobs(List<String> excludeGlobs) {
        this.excludeGlobs = excludeGlobs == null ? List.of() : List.copyOf(excludeGlobs);
    }

    public Source getParent() {
        return parent;
    }

    public void setParent(Source parent) {
        this.parent = parent;
    }

    public List<DetectedStack> getDetectedStacks() {
        return detectedStacks;
    }

    public void setDetectedStacks(List<DetectedStack> detectedStacks) {
        this.detectedStacks = detectedStacks == null ? List.of() : List.copyOf(detectedStacks);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
