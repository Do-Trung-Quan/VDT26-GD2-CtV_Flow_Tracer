-- V2: AI không còn tự chọn liên kết (bỏ mức tin cậy AI_RESOLVED) và đếm lượt trúng cache AI cho trang thống kê.

-- ---------------- Bỏ AI_RESOLVED khỏi edge_confidence ----------------
-- PostgreSQL không xóa được một giá trị enum, nên tạo lại kiểu rồi đổi cột sang kiểu mới.
ALTER TYPE edge_confidence RENAME TO edge_confidence_old;
CREATE TYPE edge_confidence AS ENUM ('CERTAIN', 'INFERRED', 'UNRESOLVED', 'RUNTIME_VERIFIED');
UPDATE ir_edges SET confidence = 'UNRESOLVED' WHERE confidence = 'AI_RESOLVED';
ALTER TABLE ir_edges
    ALTER COLUMN confidence TYPE edge_confidence USING confidence::text::edge_confidence;
DROP TYPE edge_confidence_old;

-- ---------------- Bộ đếm lượt trúng cache ----------------
ALTER TABLE ai_artifacts
    ADD COLUMN hit_count   integer     NOT NULL DEFAULT 0,
    ADD COLUMN last_hit_at timestamptz;

-- ---------------- Bỏ mục đích MIGRATION_CHECKLIST khỏi ai_purpose ----------------
ALTER TYPE ai_purpose RENAME TO ai_purpose_old;
CREATE TYPE ai_purpose AS ENUM ('PURPOSE_TEXT', 'SCENARIO_POLISH', 'MOCK_DATA', 'EDGE_RESOLUTION',
                                'LEGACY_MATCH', 'FEATURE_SUMMARY');
DELETE FROM ai_artifacts WHERE purpose = 'MIGRATION_CHECKLIST';
ALTER TABLE ai_artifacts
    ALTER COLUMN purpose TYPE ai_purpose USING purpose::text::ai_purpose;
DROP TYPE ai_purpose_old;
