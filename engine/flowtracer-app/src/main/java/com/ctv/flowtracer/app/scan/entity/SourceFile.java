package com.ctv.flowtracer.app.scan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một file mã nguồn đã đọc, kèm mã băm — cơ sở của quét lại tăng dần. Bảng {@code source_files}. */
@Entity
@Table(name = "source_files", uniqueConstraints = @UniqueConstraint(columnNames = {"source_id", "rel_path"}))
public class SourceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "rel_path", nullable = false, columnDefinition = "text")
    private String relPath;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, length = 20)
    private String language;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "parse_status", nullable = false, columnDefinition = "parse_status")
    private ParseStatus parseStatus;

    @Column(name = "parse_error", columnDefinition = "text")
    private String parseError;

    @Column(name = "last_scan_run_id", nullable = false)
    private UUID lastScanRunId;

    protected SourceFile() {}

    public SourceFile(UUID sourceId, String relPath, String contentHash, String language, int sizeBytes, UUID lastScanRunId) {
        this.sourceId = sourceId;
        this.relPath = relPath;
        this.contentHash = contentHash;
        this.language = language;
        this.sizeBytes = sizeBytes;
        this.parseStatus = ParseStatus.OK;
        this.lastScanRunId = lastScanRunId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public String getRelPath() {
        return relPath;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getLanguage() {
        return language;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(int sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public ParseStatus getParseStatus() {
        return parseStatus;
    }

    public String getParseError() {
        return parseError;
    }

    public void markParsed(ParseStatus status, String error) {
        this.parseStatus = status;
        this.parseError = error;
    }

    public UUID getLastScanRunId() {
        return lastScanRunId;
    }

    public void setLastScanRunId(UUID lastScanRunId) {
        this.lastScanRunId = lastScanRunId;
    }
}
