-- V4: mỗi lượt dùng lại nội dung AI đã lưu được ghi thành MỘT dòng nhật ký (status CACHE_HIT) để thống kê theo ngày đúng.
-- Bộ đếm cộng dồn hit_count / last_hit_at trên ai_artifacts không lọc được theo khoảng ngày nên bỏ đi.
ALTER TYPE llm_call_status ADD VALUE IF NOT EXISTS 'CACHE_HIT';

ALTER TABLE ai_artifacts
    DROP COLUMN hit_count,
    DROP COLUMN last_hit_at;
