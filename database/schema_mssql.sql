USE master;
GO
SET NOCOUNT ON;
GO

IF DB_ID(N'SecureChatDB') IS NULL
BEGIN
    PRINT 'Database SecureChatDB chưa tồn tại. Đang tạo...';
    CREATE DATABASE SecureChatDB;
END
ELSE
BEGIN
    PRINT 'Database SecureChatDB đã tồn tại.';
END
GO

USE SecureChatDB;
GO

PRINT '============================================================';
PRINT '1. DIAGNOSTIC DATABASE';
PRINT '============================================================';

SELECT DB_NAME() AS current_database, SUSER_SNAME() AS current_login, GETDATE() AS diagnostic_time;

PRINT '';
PRINT '=== TABLES HIỆN CÓ ===';
SELECT s.name AS schema_name, t.name AS table_name, t.create_date, t.modify_date
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
ORDER BY s.name, t.name;
GO

-- ============================================================
-- 2. DDL 9 BẢNG
-- ============================================================
PRINT '';
PRINT '============================================================';
PRINT '2. CREATE DATABASE TABLES';
PRINT '============================================================';
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

BEGIN TRY
    BEGIN TRANSACTION;

    -- 2.1 users
    IF OBJECT_ID(N'dbo.users', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.users...';
        CREATE TABLE dbo.users (
            id BIGINT IDENTITY(1,1) NOT NULL,
            email NVARCHAR(255) NOT NULL,
            password_hash NVARCHAR(255) NOT NULL,
            full_name NVARCHAR(120) NOT NULL,
            gender NVARCHAR(10) NULL,
            date_of_birth DATE NULL,
            status NVARCHAR(10) NOT NULL
                CONSTRAINT DF_users_status DEFAULT 'PENDING',
            created_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_users_created_at DEFAULT SYSUTCDATETIME(),
            updated_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_users_updated_at DEFAULT SYSUTCDATETIME(),
            last_seen_at DATETIME2(3) NULL,
            CONSTRAINT PK_users PRIMARY KEY (id),
            CONSTRAINT UQ_users_email UNIQUE (email),
            CONSTRAINT CK_users_gender CHECK (gender IN ('MALE','FEMALE','OTHER')),
            CONSTRAINT CK_users_status CHECK (status IN ('PENDING','ACTIVE','LOCKED'))
        );
    END
    ELSE PRINT 'Skip dbo.users: exists.';

    -- 2.2 email_otp
    IF OBJECT_ID(N'dbo.email_otp', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.email_otp...';
        CREATE TABLE dbo.email_otp (
            id BIGINT IDENTITY(1,1) NOT NULL,
            user_id BIGINT NOT NULL,
            otp_hash NVARCHAR(100) NOT NULL,
            purpose NVARCHAR(10) NOT NULL,
            expires_at DATETIME2(3) NOT NULL,
            attempt_count INT NOT NULL CONSTRAINT DF_email_otp_attempt_count DEFAULT 0,
            consumed_at DATETIME2(3) NULL,
            created_at DATETIME2(3) NOT NULL CONSTRAINT DF_email_otp_created_at DEFAULT SYSUTCDATETIME(),
            CONSTRAINT PK_email_otp PRIMARY KEY (id),
            CONSTRAINT FK_otp_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE,
            CONSTRAINT CK_email_otp_purpose CHECK (purpose IN ('REGISTER','RESET')),
            CONSTRAINT CK_email_otp_attempt_count CHECK (attempt_count >= 0)
        );
    END
    ELSE PRINT 'Skip dbo.email_otp: exists.';

    -- 2.3 groups
    IF OBJECT_ID(N'dbo.groups', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.groups...';
        CREATE TABLE dbo.groups (
            group_id BIGINT IDENTITY(1,1) NOT NULL,
            group_name NVARCHAR(100) NOT NULL,
            created_by BIGINT NOT NULL,
            created_at DATETIME2(3) NOT NULL CONSTRAINT DF_groups_created_at DEFAULT SYSUTCDATETIME(),
            CONSTRAINT PK_groups PRIMARY KEY (group_id),
            CONSTRAINT FK_groups_creator FOREIGN KEY (created_by) REFERENCES dbo.users(id),
            CONSTRAINT CK_groups_name_len CHECK (LEN(LTRIM(RTRIM(group_name))) BETWEEN 1 AND 100)
        );
    END
    ELSE PRINT 'Skip dbo.groups: exists.';

    -- 2.4 group_members
    IF OBJECT_ID(N'dbo.group_members', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.group_members...';
        CREATE TABLE dbo.group_members (
            group_id BIGINT NOT NULL,
            user_id BIGINT NOT NULL,
            role VARCHAR(16) NOT NULL,
            joined_at DATETIME2(3) NOT NULL CONSTRAINT DF_gm_joined_at DEFAULT SYSUTCDATETIME(),
            CONSTRAINT PK_group_members PRIMARY KEY (group_id, user_id),
            CONSTRAINT FK_gm_group FOREIGN KEY (group_id) REFERENCES dbo.groups(group_id) ON DELETE CASCADE,
            CONSTRAINT FK_gm_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
            CONSTRAINT CK_gm_role CHECK (role IN ('ADMIN','MEMBER'))
        );
    END
    ELSE PRINT 'Skip dbo.group_members: exists.';

    -- 2.5 attachments
    IF OBJECT_ID(N'dbo.attachments', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.attachments...';
        CREATE TABLE dbo.attachments (
            attachment_id BIGINT IDENTITY(1,1) NOT NULL,
            uploader_id BIGINT NOT NULL,
            original_filename NVARCHAR(255) NOT NULL,
            stored_filename NVARCHAR(255) NOT NULL,
            mime_type NVARCHAR(100) NULL,
            size_bytes BIGINT NOT NULL,
            sha256_hash CHAR(64) NOT NULL,
            storage_path NVARCHAR(512) NOT NULL,
            created_at DATETIME2(3) NOT NULL CONSTRAINT DF_attachments_created_at DEFAULT SYSUTCDATETIME(),
            CONSTRAINT PK_attachments PRIMARY KEY (attachment_id),
            CONSTRAINT UQ_attachments_stored_filename UNIQUE (stored_filename),
            CONSTRAINT FK_attachments_uploader FOREIGN KEY (uploader_id) REFERENCES dbo.users(id),
            CONSTRAINT CK_attachment_size CHECK (size_bytes > 0 AND size_bytes < 1048576),
            CONSTRAINT CK_attachment_hash_len CHECK (LEN(sha256_hash) = 64)
        );
    END
    ELSE PRINT 'Skip dbo.attachments: exists.';

    -- 2.6 direct_messages
    IF OBJECT_ID(N'dbo.direct_messages', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.direct_messages...';
        CREATE TABLE dbo.direct_messages (
            message_id BIGINT IDENTITY(1,1) NOT NULL,
            sender_id BIGINT NOT NULL,
            receiver_id BIGINT NOT NULL,
            content NVARCHAR(MAX) NOT NULL,
            message_type VARCHAR(16) NOT NULL CONSTRAINT DF_dm_type DEFAULT 'TEXT',
            attachment_id BIGINT NULL,
            status VARCHAR(16) NOT NULL CONSTRAINT DF_dm_status DEFAULT 'SENT',
            sent_at DATETIME2(3) NOT NULL CONSTRAINT DF_dm_sent_at DEFAULT SYSUTCDATETIME(),
            read_at DATETIME2(3) NULL,
            CONSTRAINT PK_direct_messages PRIMARY KEY (message_id),
            CONSTRAINT FK_dm_sender FOREIGN KEY (sender_id) REFERENCES dbo.users(id),
            CONSTRAINT FK_dm_receiver FOREIGN KEY (receiver_id) REFERENCES dbo.users(id),
            CONSTRAINT FK_dm_attachment FOREIGN KEY (attachment_id) REFERENCES dbo.attachments(attachment_id),
            CONSTRAINT CK_dm_type CHECK (message_type IN ('TEXT','FILE','STICKER')),
            CONSTRAINT CK_dm_status CHECK (status IN ('SENT','DELIVERED','READ')),
            CONSTRAINT CK_dm_self CHECK (sender_id <> receiver_id),
            CONSTRAINT CK_dm_content_len CHECK (LEN(content) > 0 OR attachment_id IS NOT NULL),
            CONSTRAINT CK_dm_file_consistency CHECK (
                (message_type = 'FILE' AND attachment_id IS NOT NULL) OR
                (message_type <> 'FILE' AND attachment_id IS NULL)
            ),
            CONSTRAINT CK_dm_read_at_consistency CHECK (read_at IS NULL OR status = 'READ')
        );
    END
    ELSE PRINT 'Skip dbo.direct_messages: exists.';

    -- 2.7 group_messages
    IF OBJECT_ID(N'dbo.group_messages', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.group_messages...';
        CREATE TABLE dbo.group_messages (
            message_id BIGINT IDENTITY(1,1) NOT NULL,
            sender_id BIGINT NOT NULL,
            group_id BIGINT NOT NULL,
            content NVARCHAR(MAX) NOT NULL,
            message_type VARCHAR(16) NOT NULL CONSTRAINT DF_gmsg_type DEFAULT 'TEXT',
            attachment_id BIGINT NULL,
            sent_at DATETIME2(3) NOT NULL CONSTRAINT DF_gmsg_sent_at DEFAULT SYSUTCDATETIME(),
            CONSTRAINT PK_group_messages PRIMARY KEY (message_id),
            CONSTRAINT FK_gmsg_sender FOREIGN KEY (sender_id) REFERENCES dbo.users(id),
            CONSTRAINT FK_gmsg_group FOREIGN KEY (group_id) REFERENCES dbo.groups(group_id) ON DELETE CASCADE,
            CONSTRAINT FK_gmsg_attachment FOREIGN KEY (attachment_id) REFERENCES dbo.attachments(attachment_id),
            CONSTRAINT CK_gmsg_type CHECK (message_type IN ('TEXT','FILE','STICKER')),
            CONSTRAINT CK_gmsg_content_len CHECK (LEN(content) > 0 OR attachment_id IS NOT NULL),
            CONSTRAINT CK_gmsg_file_consistency CHECK (
                (message_type = 'FILE' AND attachment_id IS NOT NULL) OR
                (message_type <> 'FILE' AND attachment_id IS NULL)
            )
        );
    END
    ELSE PRINT 'Skip dbo.group_messages: exists.';

    -- 2.8 group_message_receipts
    IF OBJECT_ID(N'dbo.group_message_receipts', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.group_message_receipts...';
        CREATE TABLE dbo.group_message_receipts (
            message_id BIGINT NOT NULL,
            user_id BIGINT NOT NULL,
            delivered_at DATETIME2(3) NULL,
            read_at DATETIME2(3) NULL,
            CONSTRAINT PK_group_message_receipts PRIMARY KEY (message_id, user_id),
            CONSTRAINT FK_receipt_message FOREIGN KEY (message_id)
                REFERENCES dbo.group_messages(message_id) ON DELETE CASCADE,
            CONSTRAINT FK_receipt_user FOREIGN KEY (user_id) REFERENCES dbo.users(id),
            CONSTRAINT CK_receipt_read_after_delivered CHECK (read_at IS NULL OR delivered_at IS NOT NULL)
        );
    END
    ELSE PRINT 'Skip dbo.group_message_receipts: exists.';

    -- 2.9 activity_logs
    IF OBJECT_ID(N'dbo.activity_logs', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.activity_logs...';
        CREATE TABLE dbo.activity_logs (
            log_id BIGINT IDENTITY(1,1) NOT NULL,
            actor_id BIGINT NULL,
            action_type VARCHAR(50) NOT NULL,
            target_type VARCHAR(50) NULL,
            target_id BIGINT NULL,
            metadata NVARCHAR(MAX) NULL,
            ip_address NVARCHAR(45) NULL,
            created_at DATETIME2(3) NOT NULL CONSTRAINT DF_activity_logs_created_at DEFAULT SYSUTCDATETIME(),
            CONSTRAINT PK_activity_logs PRIMARY KEY (log_id),
            CONSTRAINT FK_activity_logs_actor FOREIGN KEY (actor_id) REFERENCES dbo.users(id)
        );
    END
    ELSE PRINT 'Skip dbo.activity_logs: exists.';

    COMMIT TRANSACTION;
    PRINT 'DDL transaction committed successfully.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    PRINT 'DDL transaction failed.';
    SELECT ERROR_NUMBER() AS error_number, ERROR_MESSAGE() AS error_message;
    THROW;
END CATCH;
GO

-- ============================================================
-- 3. INDEXES
-- ============================================================
PRINT '';
PRINT '============================================================';
PRINT '3. CREATE INDEXES';
PRINT '============================================================';
GO

SET NOCOUNT ON;
GO

-- 3.1 OTP
IF OBJECT_ID(N'dbo.email_otp', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_otp_user_purpose' AND object_id = OBJECT_ID(N'dbo.email_otp'))
BEGIN
    CREATE INDEX ix_otp_user_purpose ON dbo.email_otp(user_id, purpose, expires_at);
END
GO

-- 3.2 Direct messages
IF OBJECT_ID(N'dbo.direct_messages', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_dm_sender_time' AND object_id = OBJECT_ID(N'dbo.direct_messages'))
BEGIN
    CREATE INDEX IX_dm_sender_time ON dbo.direct_messages(sender_id, receiver_id, sent_at DESC, message_id DESC);
END
GO

IF OBJECT_ID(N'dbo.direct_messages', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_dm_receiver_time' AND object_id = OBJECT_ID(N'dbo.direct_messages'))
BEGIN
    CREATE INDEX IX_dm_receiver_time ON dbo.direct_messages(receiver_id, sender_id, sent_at DESC, message_id DESC);
END
GO

-- 3.3 Group messages
IF OBJECT_ID(N'dbo.group_messages', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_gmsg_group_time' AND object_id = OBJECT_ID(N'dbo.group_messages'))
BEGIN
    CREATE INDEX IX_gmsg_group_time ON dbo.group_messages(group_id, sent_at DESC, message_id DESC);
END
GO

-- 3.4 Group members
IF OBJECT_ID(N'dbo.group_members', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_gm_user' AND object_id = OBJECT_ID(N'dbo.group_members'))
BEGIN
    CREATE INDEX IX_gm_user ON dbo.group_members(user_id, group_id);
END
GO

-- 3.5 Users last_seen
IF OBJECT_ID(N'dbo.users', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_users_last_seen' AND object_id = OBJECT_ID(N'dbo.users'))
BEGIN
    CREATE INDEX IX_users_last_seen ON dbo.users(last_seen_at DESC);
END
GO

-- 3.6 Attachments
IF OBJECT_ID(N'dbo.attachments', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_attachments_uploader' AND object_id = OBJECT_ID(N'dbo.attachments'))
BEGIN
    CREATE INDEX IX_attachments_uploader ON dbo.attachments(uploader_id, created_at DESC);
END
GO

IF OBJECT_ID(N'dbo.attachments', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_attachments_hash' AND object_id = OBJECT_ID(N'dbo.attachments'))
BEGIN
    CREATE INDEX IX_attachments_hash ON dbo.attachments(sha256_hash);
END
GO

-- 3.7 Receipts filtered unread
IF OBJECT_ID(N'dbo.group_message_receipts', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_receipts_user_unread' AND object_id = OBJECT_ID(N'dbo.group_message_receipts'))
BEGIN
    CREATE INDEX IX_receipts_user_unread ON dbo.group_message_receipts(user_id) WHERE read_at IS NULL;
END
GO

-- 3.8 Activity logs
IF OBJECT_ID(N'dbo.activity_logs', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_activity_logs_actor' AND object_id = OBJECT_ID(N'dbo.activity_logs'))
BEGIN
    CREATE INDEX IX_activity_logs_actor ON dbo.activity_logs(actor_id, created_at DESC);
END
GO

IF OBJECT_ID(N'dbo.activity_logs', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_activity_logs_action' AND object_id = OBJECT_ID(N'dbo.activity_logs'))
BEGIN
    CREATE INDEX IX_activity_logs_action ON dbo.activity_logs(action_type, created_at DESC);
END
GO

IF OBJECT_ID(N'dbo.activity_logs', N'U') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'IX_activity_logs_target' AND object_id = OBJECT_ID(N'dbo.activity_logs'))
BEGIN
    CREATE INDEX IX_activity_logs_target ON dbo.activity_logs(target_type, target_id, created_at DESC);
END
GO

-- ============================================================
-- 4. SEED TEST DATA (MẶC ĐỊNH OFF)
-- ============================================================
PRINT '';
PRINT '4. SEED TEST DATA';
GO

DECLARE @RunSeedData BIT = 0;

IF @RunSeedData = 0
BEGIN
    PRINT 'Seed data đang OFF. Không có dữ liệu nào được INSERT.';
END
ELSE
BEGIN
    IF OBJECT_ID(N'dbo.users', N'U') IS NULL
       OR OBJECT_ID(N'dbo.groups', N'U') IS NULL
       OR OBJECT_ID(N'dbo.group_members', N'U') IS NULL
       OR OBJECT_ID(N'dbo.direct_messages', N'U') IS NULL
       OR OBJECT_ID(N'dbo.group_messages', N'U') IS NULL
    BEGIN
        THROW 51010, 'Thiếu bảng cần thiết để seed.', 1;
    END

    DECLARE @u1 BIGINT, @u2 BIGINT, @gid BIGINT;
    SELECT TOP (1) @u1 = id FROM dbo.users WHERE status = 'ACTIVE' ORDER BY id;
    SELECT TOP (1) @u2 = id FROM dbo.users WHERE status = 'ACTIVE' AND id > @u1 ORDER BY id;

    IF @u1 IS NULL OR @u2 IS NULL
    BEGIN
        PRINT 'SKIP: Cần ít nhất 2 user ACTIVE.';
    END
    ELSE
    BEGIN
        BEGIN TRY
            BEGIN TRANSACTION;

            SELECT @gid = group_id FROM dbo.groups WHERE group_name = N'[SEED-P4] Demo Group';
            IF @gid IS NULL
            BEGIN
                INSERT INTO dbo.groups (group_name, created_by) VALUES (N'[SEED-P4] Demo Group', @u1);
                SET @gid = CONVERT(BIGINT, SCOPE_IDENTITY());
            END

            IF NOT EXISTS (SELECT 1 FROM dbo.group_members WHERE group_id = @gid AND user_id = @u1)
                INSERT INTO dbo.group_members (group_id, user_id, role) VALUES (@gid, @u1, 'ADMIN');
            IF NOT EXISTS (SELECT 1 FROM dbo.group_members WHERE group_id = @gid AND user_id = @u2)
                INSERT INTO dbo.group_members (group_id, user_id, role) VALUES (@gid, @u2, 'MEMBER');

            IF NOT EXISTS (SELECT 1 FROM dbo.direct_messages WHERE sender_id = @u1 AND receiver_id = @u2 AND content = N'[SEED-P4] Hi from u1')
                INSERT INTO dbo.direct_messages (sender_id, receiver_id, content) VALUES (@u1, @u2, N'[SEED-P4] Hi from u1');
            IF NOT EXISTS (SELECT 1 FROM dbo.direct_messages WHERE sender_id = @u2 AND receiver_id = @u1 AND content = N'[SEED-P4] Reply from u2')
                INSERT INTO dbo.direct_messages (sender_id, receiver_id, content) VALUES (@u2, @u1, N'[SEED-P4] Reply from u2');

            IF NOT EXISTS (SELECT 1 FROM dbo.group_messages WHERE sender_id = @u1 AND group_id = @gid AND content = N'[SEED-P4] Hi group')
                INSERT INTO dbo.group_messages (sender_id, group_id, content) VALUES (@u1, @gid, N'[SEED-P4] Hi group');

            COMMIT TRANSACTION;
            PRINT 'Seed data completed.';
        END TRY
        BEGIN CATCH
            IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
            THROW;
        END CATCH
    END
END
GO

-- ============================================================
-- 5. VERIFICATION (schema mới)
-- ============================================================
PRINT '';
PRINT '5. DATABASE VERIFICATION';
GO

-- 5.1 Tables
PRINT '=== 5.1 TABLE EXISTENCE ===';
SELECT table_name,
       CASE WHEN OBJECT_ID(N'dbo.' + table_name, N'U') IS NOT NULL THEN '[PASS]' ELSE '[FAIL]' END AS verification_status
FROM (VALUES
    (N'users'),(N'email_otp'),(N'groups'),(N'group_members'),
    (N'attachments'),(N'direct_messages'),(N'group_messages'),
    (N'group_message_receipts'),(N'activity_logs')
) AS t(table_name);
GO

-- 5.2 Primary keys
PRINT '=== 5.2 PRIMARY KEYS ===';
SELECT t.name AS table_name, kc.name AS primary_key_name
FROM sys.key_constraints kc
JOIN sys.tables t ON t.object_id = kc.parent_object_id
WHERE kc.type = 'PK'
  AND t.name IN ('users','email_otp','groups','group_members','attachments',
                 'direct_messages','group_messages','group_message_receipts','activity_logs')
ORDER BY t.name;
GO

-- 5.3 Foreign keys
PRINT '=== 5.3 FOREIGN KEYS ===';
SELECT fk.name AS foreign_key_name,
       OBJECT_NAME(fk.parent_object_id) AS child_table,
       OBJECT_NAME(fk.referenced_object_id) AS parent_table,
       fk.is_disabled, fk.is_not_trusted
FROM sys.foreign_keys fk
WHERE OBJECT_NAME(fk.parent_object_id) IN (
    'email_otp','groups','group_members','attachments','direct_messages',
    'group_messages','group_message_receipts','activity_logs')
ORDER BY child_table, foreign_key_name;
GO

-- 5.4 Indexes
PRINT '=== 5.4 INDEXES ===';
SELECT OBJECT_NAME(i.object_id) AS table_name, i.name AS index_name, i.type_desc, i.is_unique, i.is_primary_key
FROM sys.indexes i
WHERE i.object_id IN (
    OBJECT_ID(N'dbo.email_otp'), OBJECT_ID(N'dbo.users'),
    OBJECT_ID(N'dbo.direct_messages'), OBJECT_ID(N'dbo.group_messages'),
    OBJECT_ID(N'dbo.group_members'), OBJECT_ID(N'dbo.attachments'),
    OBJECT_ID(N'dbo.group_message_receipts'), OBJECT_ID(N'dbo.activity_logs'))
  AND i.name IS NOT NULL
ORDER BY table_name, index_name;
GO

-- 5.5 Columns kiểm tra password_hash
PRINT '=== 5.5 users.password_hash length ===';
SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='users' AND COLUMN_NAME IN ('password_hash','last_seen_at');
GO

-- 5.6 Orphan checks
PRINT '=== 5.6 ORPHAN CHECKS - EXPECT 0 ===';
IF OBJECT_ID(N'dbo.email_otp', N'U') IS NOT NULL
    SELECT 'email_otp_orphan_user' AS check_name, COUNT_BIG(*) AS orphan_count
    FROM dbo.email_otp otp LEFT JOIN dbo.users u ON u.id = otp.user_id WHERE u.id IS NULL;

IF OBJECT_ID(N'dbo.direct_messages', N'U') IS NOT NULL
    SELECT 'direct_messages_orphan' AS check_name, COUNT_BIG(*) AS orphan_count
    FROM dbo.direct_messages dm
    LEFT JOIN dbo.users s ON s.id = dm.sender_id
    LEFT JOIN dbo.users r ON r.id = dm.receiver_id
    LEFT JOIN dbo.attachments a ON a.attachment_id = dm.attachment_id
    WHERE s.id IS NULL OR r.id IS NULL OR (dm.attachment_id IS NOT NULL AND a.attachment_id IS NULL);

IF OBJECT_ID(N'dbo.group_messages', N'U') IS NOT NULL
    SELECT 'group_messages_orphan' AS check_name, COUNT_BIG(*) AS orphan_count
    FROM dbo.group_messages gm
    LEFT JOIN dbo.users s ON s.id = gm.sender_id
    LEFT JOIN dbo.groups g ON g.group_id = gm.group_id
    LEFT JOIN dbo.attachments a ON a.attachment_id = gm.attachment_id
    WHERE s.id IS NULL OR g.group_id IS NULL OR (gm.attachment_id IS NOT NULL AND a.attachment_id IS NULL);

IF OBJECT_ID(N'dbo.group_message_receipts', N'U') IS NOT NULL
    SELECT 'group_message_receipts_orphan' AS check_name, COUNT_BIG(*) AS orphan_count
    FROM dbo.group_message_receipts r
    LEFT JOIN dbo.group_messages m ON m.message_id = r.message_id
    LEFT JOIN dbo.users u ON u.id = r.user_id
    WHERE m.message_id IS NULL OR u.id IS NULL;
GO

-- 5.7 Group admin check
PRINT '=== 5.7 GROUP ADMIN CHECK ===';
IF OBJECT_ID(N'dbo.groups', N'U') IS NOT NULL AND OBJECT_ID(N'dbo.group_members', N'U') IS NOT NULL
BEGIN
    SELECT g.group_id, g.group_name,
           SUM(CASE WHEN gm.role='ADMIN' THEN 1 ELSE 0 END) AS admin_count,
           CASE WHEN SUM(CASE WHEN gm.role='ADMIN' THEN 1 ELSE 0 END) > 0 THEN '[PASS]' ELSE '[FAIL]' END AS verification_status
    FROM dbo.groups g
    LEFT JOIN dbo.group_members gm ON gm.group_id = g.group_id
    GROUP BY g.group_id, g.group_name
    ORDER BY g.group_id;
END
GO

-- ============================================================
-- 6. OPTIONAL ROLLBACK (MẶC ĐỊNH OFF)
-- ============================================================
PRINT '';
PRINT '6. OPTIONAL ROLLBACK (M2 tables only)';
GO

DECLARE @ExecuteRollback BIT = 0;
IF @ExecuteRollback = 0
BEGIN
    PRINT 'Rollback OFF.';
END
ELSE
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION;
        -- Xóa theo thứ tự FK
        IF OBJECT_ID(N'dbo.group_message_receipts', N'U') IS NOT NULL DROP TABLE dbo.group_message_receipts;
        IF OBJECT_ID(N'dbo.activity_logs', N'U') IS NOT NULL DROP TABLE dbo.activity_logs;
        IF OBJECT_ID(N'dbo.group_messages', N'U') IS NOT NULL DROP TABLE dbo.group_messages;
        IF OBJECT_ID(N'dbo.direct_messages', N'U') IS NOT NULL DROP TABLE dbo.direct_messages;
        IF OBJECT_ID(N'dbo.attachments', N'U') IS NOT NULL DROP TABLE dbo.attachments;
        IF OBJECT_ID(N'dbo.group_members', N'U') IS NOT NULL DROP TABLE dbo.group_members;
        IF OBJECT_ID(N'dbo.groups', N'U') IS NOT NULL DROP TABLE dbo.groups;
        IF OBJECT_ID(N'dbo.email_otp', N'U') IS NOT NULL DROP TABLE dbo.email_otp;
        IF OBJECT_ID(N'dbo.users', N'U') IS NOT NULL DROP TABLE dbo.users;
        COMMIT TRANSACTION;
        PRINT 'Rollback completed.';
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH
END
GO