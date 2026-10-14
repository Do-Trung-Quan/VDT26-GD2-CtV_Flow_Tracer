-- V5: hoàn tác V4 theo yêu cầu (gỡ thống kê "trúng cache"): khôi phục hai cột bộ đếm trên ai_artifacts và xóa các dòng nhật ký CACHE_HIT.
-- Giá trị enum CACHE_HIT vẫn còn trong llm_call_status (PostgreSQL không xóa được một giá trị enum); không còn dòng nào dùng nó.
ALTER TABLE ai_artifacts
    ADD COLUMN IF NOT EXISTS hit_count   integer     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_hit_at timestamptz;

DELETE FROM llm_call_logs WHERE status = 'CACHE_HIT';
