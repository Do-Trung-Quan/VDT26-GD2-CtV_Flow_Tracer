-- V6: gỡ hẳn nhóm chức năng (Feature) — chức năng Mở rộng này đã bị loại khỏi lộ trình; hai bảng được tạo sẵn ở V1 chưa từng được dùng.
-- Nếu sau này làm lại thì thêm migration mới tạo lại bảng.
DROP TABLE IF EXISTS feature_members;
DROP TABLE IF EXISTS features;
DROP TYPE IF EXISTS feature_origin;
