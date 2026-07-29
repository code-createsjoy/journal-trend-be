-- Gộp STUDENT/LECTURER/RESEARCHER thành 1 role USER duy nhất.
-- Chạy thủ công 1 lần trước khi deploy code mới (project không dùng Flyway tự động).
-- Bảng thật là app_users (không phải "users"), và có CHECK constraint riêng trên
-- user_role (tự sinh, không nằm trong entity/Hibernate) chặn giá trị 'USER' nếu không
-- drop/re-add — đã xác nhận qua regression test thực tế (CK__app_users__user___32024716).

BEGIN TRAN;

-- 1) Drop CHECK constraint cũ (tên tự sinh, chỉ cho phép STUDENT/LECTURER/RESEARCHER/ADMIN/SUPER_ADMIN)
ALTER TABLE app_users DROP CONSTRAINT CK__app_users__user___32024716;

-- 2) Migrate dữ liệu cũ
UPDATE app_users
SET user_role = 'USER'
WHERE user_role IN ('STUDENT', 'LECTURER', 'RESEARCHER');

-- 3) Add lại CHECK constraint với 3 giá trị mới
ALTER TABLE app_users ADD CONSTRAINT CK_app_users_user_role CHECK (user_role IN ('USER', 'ADMIN', 'SUPER_ADMIN'));

COMMIT TRAN;

-- Đơn xin đổi role cũ (nếu bảng còn tồn tại) không còn được backend dùng tới,
-- có thể xóa bảng sau khi xác nhận không cần dữ liệu lịch sử này nữa:
-- DROP TABLE IF EXISTS role_upgrade_requests;
-- DROP TABLE IF EXISTS role_change_logs;
