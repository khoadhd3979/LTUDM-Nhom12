/*
    ============================================================
    M3-B migration — thêm users.is_system_admin
    ============================================================

    Mục đích:
      Bổ sung cột users.is_system_admin cho database M2 đã tồn tại
      (không mất dữ liệu cũ).

    Áp dụng cho:
      - Database đang ở schema M2-B (9 bảng, chưa có is_system_admin).
      - Fresh database dùng schema_mssql.sql mới là đủ, KHÔNG cần chạy file này.

    An toàn:
      - Guarded bằng COL_LENGTH → chạy lại không lỗi, không tạo cột/constraint trùng.
      - KHÔNG DROP DATABASE / DROP TABLE / xóa dữ liệu.
      - Tất cả user hiện có nhận is_system_admin = 0.

    Không dùng:
      - database/00_drop_database_reset.sql
*/
USE SecureChatDB;
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

PRINT '=== M3-B migration: users.is_system_admin ===';
GO

-- 1. Thêm cột nếu chưa tồn tại
IF COL_LENGTH('dbo.users', 'is_system_admin') IS NULL
BEGIN
    PRINT 'Adding column dbo.users.is_system_admin...';
    ALTER TABLE dbo.users
        ADD is_system_admin BIT NOT NULL
            CONSTRAINT DF_users_is_system_admin DEFAULT 0;
    PRINT 'Column added.';
END
ELSE
BEGIN
    PRINT 'Column dbo.users.is_system_admin already exists — skip.';
END
GO

-- 2. Đảm bảo giá trị NOT NULL được gán cho mọi row (idempotent)
UPDATE dbo.users
SET is_system_admin = 0
WHERE is_system_admin IS NULL;
GO

-- 3. Đảm bảo default constraint tồn tại (guard theo tên)
IF NOT EXISTS (
    SELECT 1
    FROM sys.default_constraints dc
    JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
    WHERE dc.parent_object_id = OBJECT_ID('dbo.users')
      AND c.name = 'is_system_admin'
)
BEGIN
    PRINT 'Adding default constraint DF_users_is_system_admin...';
    ALTER TABLE dbo.users
        ADD CONSTRAINT DF_users_is_system_admin DEFAULT 0 FOR is_system_admin;
END
GO

-- 4. Verification
PRINT '=== Verification ===';
SELECT
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'dbo'
  AND TABLE_NAME   = 'users'
  AND COLUMN_NAME  = 'is_system_admin';
GO

SELECT
    SUM(CASE WHEN is_system_admin = 1 THEN 1 ELSE 0 END) AS system_admin_count,
    SUM(CASE WHEN is_system_admin = 0 THEN 1 ELSE 0 END) AS regular_user_count
FROM dbo.users;
GO

PRINT '=== M3-B migration: DONE ===';
GO