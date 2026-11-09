/*
    ============================================================
    SECURECHAT-JAVA - SQL SERVER DATABASE
    PHASE 1 + PHASE 4
    ============================================================

    Database: SecureChatDB
    SQL Server

    Bao gồm:
    1. Diagnostic & khởi tạo database
    2. DDL schema Phase 1 + Phase 4
    3. Indexes
    4. Seed test data
    5. Verification
    6. Optional rollback

    LƯU Ý:
    - Không DROP users hoặc email_otp.
    - Không tự động xóa dữ liệu hiện tại.
    - Seed data mặc định OFF.
    - Rollback mặc định OFF.
    - Chạy toàn bộ file trong SQL Server Management Studio.
*/


-- ============================================================
-- 1. DIAGNOSTIC & KHỞI TẠO DATABASE
-- ============================================================

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

SELECT
    DB_NAME() AS current_database,
    SUSER_SNAME() AS current_login,
    GETDATE() AS diagnostic_time;

PRINT '';
PRINT '=== TABLES HIỆN CÓ ===';

SELECT
    s.name AS schema_name,
    t.name AS table_name,
    t.create_date,
    t.modify_date
FROM sys.tables t
JOIN sys.schemas s
    ON s.schema_id = t.schema_id
ORDER BY s.name, t.name;


PRINT '';
PRINT '=== COLUMNS: dbo.users ===';

IF OBJECT_ID(N'dbo.users', N'U') IS NOT NULL
BEGIN
    SELECT
        COLUMN_NAME,
        DATA_TYPE,
        CHARACTER_MAXIMUM_LENGTH,
        IS_NULLABLE,
        COLUMN_DEFAULT
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'dbo'
      AND TABLE_NAME = 'users'
    ORDER BY ORDINAL_POSITION;
END
ELSE
BEGIN
    PRINT 'dbo.users chưa tồn tại.';
END


PRINT '';
PRINT '=== COLUMNS: dbo.email_otp ===';

IF OBJECT_ID(N'dbo.email_otp', N'U') IS NOT NULL
BEGIN
    SELECT
        COLUMN_NAME,
        DATA_TYPE,
        CHARACTER_MAXIMUM_LENGTH,
        IS_NULLABLE,
        COLUMN_DEFAULT
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'dbo'
      AND TABLE_NAME = 'email_otp'
    ORDER BY ORDINAL_POSITION;
END
ELSE
BEGIN
    PRINT 'dbo.email_otp chưa tồn tại.';
END


PRINT '';
PRINT '=== PHASE 4 TABLES HIỆN CÓ ===';

SELECT
    s.name AS schema_name,
    t.name AS table_name
FROM sys.tables t
JOIN sys.schemas s
    ON s.schema_id = t.schema_id
WHERE t.name IN (
    'groups',
    'group_members',
    'direct_messages',
    'group_messages'
)
ORDER BY t.name;


PRINT '';
PRINT '=== FOREIGN KEYS THAM CHIẾU dbo.users ===';

IF OBJECT_ID(N'dbo.users', N'U') IS NOT NULL
BEGIN
    SELECT
        fk.name AS foreign_key_name,
        OBJECT_NAME(fk.parent_object_id) AS child_table,
        OBJECT_NAME(fk.referenced_object_id) AS referenced_table
    FROM sys.foreign_keys fk
    WHERE fk.referenced_object_id = OBJECT_ID(N'dbo.users');
END
GO


-- ============================================================
-- 2. DDL SCHEMA TỔNG HỢP (PHASE 1 + PHASE 4)
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

    ------------------------------------------------------------
    -- 2.1 USERS - PHASE 1
    -- Giữ nguyên cấu trúc schema_mssql.sql gốc.
    ------------------------------------------------------------

    IF OBJECT_ID(N'dbo.users', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.users...';

        CREATE TABLE dbo.users (
            id BIGINT IDENTITY(1,1) NOT NULL,

            email NVARCHAR(255) NOT NULL,
            password_hash NVARCHAR(100) NOT NULL,
            full_name NVARCHAR(120) NOT NULL,

            gender NVARCHAR(10) NULL,
            date_of_birth DATE NULL,

            status NVARCHAR(10) NOT NULL
                CONSTRAINT DF_users_status DEFAULT 'PENDING',

            created_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_users_created_at DEFAULT SYSUTCDATETIME(),

            updated_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_users_updated_at DEFAULT SYSUTCDATETIME(),

            CONSTRAINT PK_users PRIMARY KEY (id),

            CONSTRAINT UQ_users_email UNIQUE (email),

            CONSTRAINT CK_users_gender
                CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')),

            CONSTRAINT CK_users_status
                CHECK (status IN ('PENDING', 'ACTIVE', 'LOCKED'))
        );
    END
    ELSE
    BEGIN
        PRINT 'Skip dbo.users: table already exists.';
    END


    ------------------------------------------------------------
    -- 2.2 EMAIL_OTP - PHASE 1
    ------------------------------------------------------------

    IF OBJECT_ID(N'dbo.email_otp', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.email_otp...';

        CREATE TABLE dbo.email_otp (
            id BIGINT IDENTITY(1,1) NOT NULL,

            user_id BIGINT NOT NULL,
            otp_hash NVARCHAR(100) NOT NULL,

            purpose NVARCHAR(10) NOT NULL,

            expires_at DATETIME2(3) NOT NULL,

            attempt_count INT NOT NULL
                CONSTRAINT DF_email_otp_attempt_count DEFAULT 0,

            consumed_at DATETIME2(3) NULL,

            created_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_email_otp_created_at DEFAULT SYSUTCDATETIME(),

            CONSTRAINT PK_email_otp PRIMARY KEY (id),

            CONSTRAINT FK_otp_user
                FOREIGN KEY (user_id)
                REFERENCES dbo.users(id)
                ON DELETE CASCADE,

            CONSTRAINT CK_email_otp_purpose
                CHECK (purpose IN ('REGISTER', 'RESET')),

            CONSTRAINT CK_email_otp_attempt_count
                CHECK (attempt_count >= 0)
        );
    END
    ELSE
    BEGIN
        PRINT 'Skip dbo.email_otp: table already exists.';
    END


    ------------------------------------------------------------
    -- Kiểm tra bảng users trước khi tạo Phase 4.
    -- Schema gốc xác nhận users.id là BIGINT.
    ------------------------------------------------------------

    IF OBJECT_ID(N'dbo.users', N'U') IS NULL
    BEGIN
        THROW 51001, 'dbo.users không tồn tại. Không thể tạo Phase 4.', 1;
    END

    IF NOT EXISTS (
        SELECT 1
        FROM sys.columns
        WHERE object_id = OBJECT_ID(N'dbo.users')
          AND name = 'id'
          AND TYPE_NAME(user_type_id) = 'bigint'
    )
    BEGIN
        THROW 51002, 'dbo.users.id không phải BIGINT. Hãy kiểm tra schema thực tế trước khi migration.', 1;
    END


    ------------------------------------------------------------
    -- 2.3 GROUPS - PHASE 4
    ------------------------------------------------------------

    IF OBJECT_ID(N'dbo.groups', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.groups...';

        CREATE TABLE dbo.groups (
            group_id BIGINT IDENTITY(1,1) NOT NULL,

            group_name NVARCHAR(100) NOT NULL,

            created_by BIGINT NOT NULL,

            created_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_groups_created_at DEFAULT SYSUTCDATETIME(),

            CONSTRAINT PK_groups PRIMARY KEY (group_id),

            CONSTRAINT FK_groups_creator
                FOREIGN KEY (created_by)
                REFERENCES dbo.users(id),

            CONSTRAINT CK_groups_name_len
                CHECK (
                    LEN(LTRIM(RTRIM(group_name))) BETWEEN 1 AND 100
                )
        );
    END
    ELSE
    BEGIN
        PRINT 'Skip dbo.groups: table already exists.';
    END


    ------------------------------------------------------------
    -- 2.4 GROUP_MEMBERS - PHASE 4
    ------------------------------------------------------------

    IF OBJECT_ID(N'dbo.group_members', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.group_members...';

        CREATE TABLE dbo.group_members (
            group_id BIGINT NOT NULL,
            user_id BIGINT NOT NULL,

            role VARCHAR(16) NOT NULL,

            joined_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_gm_joined_at DEFAULT SYSUTCDATETIME(),

            CONSTRAINT PK_group_members
                PRIMARY KEY (group_id, user_id),

            CONSTRAINT FK_gm_group
                FOREIGN KEY (group_id)
                REFERENCES dbo.groups(group_id)
                ON DELETE CASCADE,

            CONSTRAINT FK_gm_user
                FOREIGN KEY (user_id)
                REFERENCES dbo.users(id),

            CONSTRAINT CK_gm_role
                CHECK (role IN ('ADMIN', 'MEMBER'))
        );
    END
    ELSE
    BEGIN
        PRINT 'Skip dbo.group_members: table already exists.';
    END


    ------------------------------------------------------------
    -- 2.5 DIRECT_MESSAGES - PHASE 4
    ------------------------------------------------------------

    IF OBJECT_ID(N'dbo.direct_messages', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.direct_messages...';

        CREATE TABLE dbo.direct_messages (
            message_id BIGINT IDENTITY(1,1) NOT NULL,

            sender_id BIGINT NOT NULL,
            receiver_id BIGINT NOT NULL,

            content NVARCHAR(MAX) NOT NULL,

            message_type VARCHAR(16) NOT NULL
                CONSTRAINT DF_dm_type DEFAULT 'TEXT',

            file_path NVARCHAR(512) NULL,

            status VARCHAR(16) NOT NULL
                CONSTRAINT DF_dm_status DEFAULT 'SENT',

            sent_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_dm_sent_at DEFAULT SYSUTCDATETIME(),

            CONSTRAINT PK_direct_messages
                PRIMARY KEY (message_id),

            CONSTRAINT FK_dm_sender
                FOREIGN KEY (sender_id)
                REFERENCES dbo.users(id),

            CONSTRAINT FK_dm_receiver
                FOREIGN KEY (receiver_id)
                REFERENCES dbo.users(id),

            CONSTRAINT CK_dm_type
                CHECK (message_type IN ('TEXT', 'FILE', 'STICKER')),

            CONSTRAINT CK_dm_status
                CHECK (status IN ('SENT', 'DELIVERED', 'READ')),

            CONSTRAINT CK_dm_self
                CHECK (sender_id <> receiver_id),

            CONSTRAINT CK_dm_content_len
                CHECK (LEN(content) > 0 OR file_path IS NOT NULL)
        );
    END
    ELSE
    BEGIN
        PRINT 'Skip dbo.direct_messages: table already exists.';
    END


    ------------------------------------------------------------
    -- 2.6 GROUP_MESSAGES - PHASE 4
    --
    -- Không dùng status chung cho group message.
    -- Trạng thái delivery/read của từng thành viên cần được
    -- thiết kế riêng nếu có yêu cầu triển khai receipt.
    ------------------------------------------------------------

    IF OBJECT_ID(N'dbo.group_messages', N'U') IS NULL
    BEGIN
        PRINT 'Creating dbo.group_messages...';

        CREATE TABLE dbo.group_messages (
            message_id BIGINT IDENTITY(1,1) NOT NULL,

            sender_id BIGINT NOT NULL,
            group_id BIGINT NOT NULL,

            content NVARCHAR(MAX) NOT NULL,

            message_type VARCHAR(16) NOT NULL
                CONSTRAINT DF_gmsg_type DEFAULT 'TEXT',

            file_path NVARCHAR(512) NULL,

            sent_at DATETIME2(3) NOT NULL
                CONSTRAINT DF_gmsg_sent_at DEFAULT SYSUTCDATETIME(),

            CONSTRAINT PK_group_messages
                PRIMARY KEY (message_id),

            CONSTRAINT FK_gmsg_sender
                FOREIGN KEY (sender_id)
                REFERENCES dbo.users(id),

            CONSTRAINT FK_gmsg_group
                FOREIGN KEY (group_id)
                REFERENCES dbo.groups(group_id)
                ON DELETE CASCADE,

            CONSTRAINT CK_gmsg_type
                CHECK (message_type IN ('TEXT', 'FILE', 'STICKER')),

            CONSTRAINT CK_gmsg_content_len
                CHECK (LEN(content) > 0 OR file_path IS NOT NULL)
        );
    END
    ELSE
    BEGIN
        PRINT 'Skip dbo.group_messages: table already exists.';
    END


    COMMIT TRANSACTION;

    PRINT 'DDL transaction committed successfully.';
END TRY
BEGIN CATCH

    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;

    PRINT 'DDL transaction failed.';

    SELECT
        ERROR_NUMBER() AS error_number,
        ERROR_SEVERITY() AS error_severity,
        ERROR_STATE() AS error_state,
        ERROR_PROCEDURE() AS error_procedure,
        ERROR_LINE() AS error_line,
        ERROR_MESSAGE() AS error_message;

    THROW;
END CATCH;
GO


-- ============================================================
-- 3. KHỞI TẠO INDEXES TỐI ƯU
-- ============================================================

PRINT '';
PRINT '============================================================';
PRINT '3. CREATE INDEXES';
PRINT '============================================================';
GO

SET NOCOUNT ON;
GO

---------------------------------------------------------------
-- 3.1 OTP INDEX
---------------------------------------------------------------

IF OBJECT_ID(N'dbo.email_otp', N'U') IS NOT NULL
AND NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'ix_otp_user_purpose'
      AND object_id = OBJECT_ID(N'dbo.email_otp')
)
BEGIN
    PRINT 'Creating ix_otp_user_purpose...';

    CREATE INDEX ix_otp_user_purpose
        ON dbo.email_otp(user_id, purpose, expires_at);
END
ELSE
BEGIN
    PRINT 'Skip ix_otp_user_purpose.';
END
GO


---------------------------------------------------------------
-- 3.2 DIRECT MESSAGE INDEXES
---------------------------------------------------------------

IF OBJECT_ID(N'dbo.direct_messages', N'U') IS NOT NULL
AND NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_dm_sender_time'
      AND object_id = OBJECT_ID(N'dbo.direct_messages')
)
BEGIN
    PRINT 'Creating IX_dm_sender_time...';

    CREATE INDEX IX_dm_sender_time
        ON dbo.direct_messages (
            sender_id,
            receiver_id,
            sent_at DESC,
            message_id DESC
        );
END
GO


IF OBJECT_ID(N'dbo.direct_messages', N'U') IS NOT NULL
AND NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_dm_receiver_time'
      AND object_id = OBJECT_ID(N'dbo.direct_messages')
)
BEGIN
    PRINT 'Creating IX_dm_receiver_time...';

    CREATE INDEX IX_dm_receiver_time
        ON dbo.direct_messages (
            receiver_id,
            sender_id,
            sent_at DESC,
            message_id DESC
        );
END
GO


---------------------------------------------------------------
-- 3.3 GROUP MESSAGE INDEX
---------------------------------------------------------------

IF OBJECT_ID(N'dbo.group_messages', N'U') IS NOT NULL
AND NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_gmsg_group_time'
      AND object_id = OBJECT_ID(N'dbo.group_messages')
)
BEGIN
    PRINT 'Creating IX_gmsg_group_time...';

    CREATE INDEX IX_gmsg_group_time
        ON dbo.group_messages (
            group_id,
            sent_at DESC,
            message_id DESC
        );
END
GO


---------------------------------------------------------------
-- 3.4 GROUP MEMBER INDEX
---------------------------------------------------------------

IF OBJECT_ID(N'dbo.group_members', N'U') IS NOT NULL
AND NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_gm_user'
      AND object_id = OBJECT_ID(N'dbo.group_members')
)
BEGIN
    PRINT 'Creating IX_gm_user...';

    CREATE INDEX IX_gm_user
        ON dbo.group_members(user_id, group_id);
END
GO


-- ============================================================
-- 4. SEED TEST DATA (DỮ LIỆU MẪU)
-- ============================================================

PRINT '';
PRINT '============================================================';
PRINT '4. SEED TEST DATA';
PRINT '============================================================';
GO

/*
    Mặc định OFF để tránh tự ý thêm dữ liệu vào database thật.

    Muốn tạo dữ liệu mẫu:
        Đổi @RunSeedData = 0 thành @RunSeedData = 1.

    Yêu cầu:
        - dbo.users phải có ít nhất 2 user.
        - Script không tạo hoặc thay đổi user.
        - Chỉ tạo dữ liệu Phase 4 có marker [SEED-P4].
*/

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
        THROW 51010, 'Thiếu bảng cần thiết để seed Phase 4.', 1;
    END

    DECLARE @u1 BIGINT;
    DECLARE @u2 BIGINT;
    DECLARE @gid BIGINT;

    SELECT TOP (1)
        @u1 = id
    FROM dbo.users
    WHERE status = 'ACTIVE'
    ORDER BY id;

    SELECT TOP (1)
        @u2 = id
    FROM dbo.users
    WHERE status = 'ACTIVE'
      AND id > @u1
    ORDER BY id;

    IF @u1 IS NULL OR @u2 IS NULL
    BEGIN
        PRINT 'SKIP: Cần ít nhất 2 user ACTIVE trong dbo.users.';
    END
    ELSE
    BEGIN
        BEGIN TRY
            BEGIN TRANSACTION;

            PRINT 'Seed users:';
            SELECT @u1 AS user_1, @u2 AS user_2;

            ----------------------------------------------------
            -- GROUP
            ----------------------------------------------------

            SELECT
                @gid = group_id
            FROM dbo.groups
            WHERE group_name = N'[SEED-P4] Demo Group';

            IF @gid IS NULL
            BEGIN
                INSERT INTO dbo.groups (
                    group_name,
                    created_by
                )
                VALUES (
                    N'[SEED-P4] Demo Group',
                    @u1
                );

                SET @gid = CONVERT(BIGINT, SCOPE_IDENTITY());

                PRINT 'Created seed group.';
            END
            ELSE
            BEGIN
                PRINT 'Seed group already exists.';
            END

            ----------------------------------------------------
            -- GROUP MEMBERS
            -- Luôn kiểm tra từng membership để có thể chạy lại.
            ----------------------------------------------------

            IF NOT EXISTS (
                SELECT 1
                FROM dbo.group_members
                WHERE group_id = @gid
                  AND user_id = @u1
            )
            BEGIN
                INSERT INTO dbo.group_members (
                    group_id,
                    user_id,
                    role
                )
                VALUES (
                    @gid,
                    @u1,
                    'ADMIN'
                );
            END

            IF NOT EXISTS (
                SELECT 1
                FROM dbo.group_members
                WHERE group_id = @gid
                  AND user_id = @u2
            )
            BEGIN
                INSERT INTO dbo.group_members (
                    group_id,
                    user_id,
                    role
                )
                VALUES (
                    @gid,
                    @u2,
                    'MEMBER'
                );
            END

            ----------------------------------------------------
            -- DIRECT MESSAGES
            ----------------------------------------------------

            IF NOT EXISTS (
                SELECT 1
                FROM dbo.direct_messages
                WHERE sender_id = @u1
                  AND receiver_id = @u2
                  AND content = N'[SEED-P4] Hello from u1 to u2'
            )
            BEGIN
                INSERT INTO dbo.direct_messages (
                    sender_id,
                    receiver_id,
                    content
                )
                VALUES (
                    @u1,
                    @u2,
                    N'[SEED-P4] Hello from u1 to u2'
                );
            END

            IF NOT EXISTS (
                SELECT 1
                FROM dbo.direct_messages
                WHERE sender_id = @u2
                  AND receiver_id = @u1
                  AND content = N'[SEED-P4] Reply from u2 to u1'
            )
            BEGIN
                INSERT INTO dbo.direct_messages (
                    sender_id,
                    receiver_id,
                    content
                )
                VALUES (
                    @u2,
                    @u1,
                    N'[SEED-P4] Reply from u2 to u1'
                );
            END

            ----------------------------------------------------
            -- GROUP MESSAGES
            ----------------------------------------------------

            IF NOT EXISTS (
                SELECT 1
                FROM dbo.group_messages
                WHERE sender_id = @u1
                  AND group_id = @gid
                  AND content = N'[SEED-P4] Hello group from u1'
            )
            BEGIN
                INSERT INTO dbo.group_messages (
                    sender_id,
                    group_id,
                    content
                )
                VALUES (
                    @u1,
                    @gid,
                    N'[SEED-P4] Hello group from u1'
                );
            END

            IF NOT EXISTS (
                SELECT 1
                FROM dbo.group_messages
                WHERE sender_id = @u2
                  AND group_id = @gid
                  AND content = N'[SEED-P4] Hello group from u2'
            )
            BEGIN
                INSERT INTO dbo.group_messages (
                    sender_id,
                    group_id,
                    content
                )
                VALUES (
                    @u2,
                    @gid,
                    N'[SEED-P4] Hello group from u2'
                );
            END

            COMMIT TRANSACTION;

            PRINT 'Seed data completed.';
        END TRY
        BEGIN CATCH

            IF @@TRANCOUNT > 0
                ROLLBACK TRANSACTION;

            SELECT
                ERROR_NUMBER() AS error_number,
                ERROR_MESSAGE() AS error_message;

            THROW;
        END CATCH
    END
END
GO


-- ============================================================
-- 5. VERIFICATION - KIỂM TRA TOÀN VẸN CSDL
-- ============================================================

PRINT '';
PRINT '============================================================';
PRINT '5. DATABASE VERIFICATION';
PRINT '============================================================';
GO

SET NOCOUNT ON;
GO

---------------------------------------------------------------
-- 5.1 TABLE EXISTENCE
---------------------------------------------------------------

PRINT '=== 5.1 TABLE EXISTENCE ===';

SELECT
    table_name,
    CASE
        WHEN OBJECT_ID(N'dbo.' + table_name, N'U') IS NOT NULL
            THEN '[PASS]'
        ELSE '[FAIL]'
    END AS verification_status
FROM (
    VALUES
        (N'users'),
        (N'email_otp'),
        (N'groups'),
        (N'group_members'),
        (N'direct_messages'),
        (N'group_messages')
) AS expected_tables(table_name);


---------------------------------------------------------------
-- 5.2 PRIMARY KEYS
---------------------------------------------------------------

PRINT '';
PRINT '=== 5.2 PRIMARY KEYS ===';

SELECT
    t.name AS table_name,
    kc.name AS primary_key_name
FROM sys.key_constraints kc
JOIN sys.tables t
    ON t.object_id = kc.parent_object_id
WHERE kc.type = 'PK'
  AND t.name IN (
      'users',
      'email_otp',
      'groups',
      'group_members',
      'direct_messages',
      'group_messages'
  )
ORDER BY t.name;


---------------------------------------------------------------
-- 5.3 FOREIGN KEYS
---------------------------------------------------------------

PRINT '';
PRINT '=== 5.3 FOREIGN KEYS ===';

SELECT
    fk.name AS foreign_key_name,
    OBJECT_SCHEMA_NAME(fk.parent_object_id) AS child_schema,
    OBJECT_NAME(fk.parent_object_id) AS child_table,
    OBJECT_NAME(fk.referenced_object_id) AS parent_table,
    fk.is_disabled,
    fk.is_not_trusted
FROM sys.foreign_keys fk
WHERE OBJECT_NAME(fk.parent_object_id) IN (
    'email_otp',
    'groups',
    'group_members',
    'direct_messages',
    'group_messages'
)
ORDER BY child_table, foreign_key_name;


---------------------------------------------------------------
-- 5.4 INDEXES
---------------------------------------------------------------

PRINT '';
PRINT '=== 5.4 INDEXES ===';

SELECT
    OBJECT_NAME(i.object_id) AS table_name,
    i.name AS index_name,
    i.type_desc,
    i.is_unique,
    i.is_primary_key
FROM sys.indexes i
WHERE i.object_id IN (
    OBJECT_ID(N'dbo.email_otp'),
    OBJECT_ID(N'dbo.direct_messages'),
    OBJECT_ID(N'dbo.group_messages'),
    OBJECT_ID(N'dbo.group_members')
)
AND i.name IS NOT NULL
ORDER BY table_name, index_name;


---------------------------------------------------------------
-- 5.5 ROW COUNTS
---------------------------------------------------------------

PRINT '';
PRINT '=== 5.5 ROW COUNTS ===';

SELECT
    t.name AS table_name,
    SUM(p.rows) AS row_count
FROM sys.tables t
JOIN sys.partitions p
    ON p.object_id = t.object_id
WHERE p.index_id IN (0, 1)
  AND t.name IN (
      'users',
      'email_otp',
      'groups',
      'group_members',
      'direct_messages',
      'group_messages'
  )
GROUP BY t.name
ORDER BY t.name;


---------------------------------------------------------------
-- 5.6 ORPHAN CHECKS
-- Các kết quả mong đợi: 0.
---------------------------------------------------------------

PRINT '';
PRINT '=== 5.6 ORPHAN CHECKS - EXPECT 0 ===';

IF OBJECT_ID(N'dbo.email_otp', N'U') IS NOT NULL
BEGIN
    SELECT
        'email_otp_orphan_user' AS check_name,
        COUNT_BIG(*) AS orphan_count
    FROM dbo.email_otp otp
    LEFT JOIN dbo.users u
        ON u.id = otp.user_id
    WHERE u.id IS NULL;
END

IF OBJECT_ID(N'dbo.groups', N'U') IS NOT NULL
BEGIN
    SELECT
        'groups_orphan_creator' AS check_name,
        COUNT_BIG(*) AS orphan_count
    FROM dbo.groups g
    LEFT JOIN dbo.users u
        ON u.id = g.created_by
    WHERE u.id IS NULL;
END

IF OBJECT_ID(N'dbo.group_members', N'U') IS NOT NULL
BEGIN
    SELECT
        'group_members_orphan_group' AS check_name,
        COUNT_BIG(*) AS orphan_count
    FROM dbo.group_members gm
    LEFT JOIN dbo.groups g
        ON g.group_id = gm.group_id
    WHERE g.group_id IS NULL;

    SELECT
        'group_members_orphan_user' AS check_name,
        COUNT_BIG(*) AS orphan_count
    FROM dbo.group_members gm
    LEFT JOIN dbo.users u
        ON u.id = gm.user_id
    WHERE u.id IS NULL;
END

IF OBJECT_ID(N'dbo.direct_messages', N'U') IS NOT NULL
BEGIN
    SELECT
        'direct_messages_orphan_user' AS check_name,
        COUNT_BIG(*) AS orphan_count
    FROM dbo.direct_messages dm
    LEFT JOIN dbo.users sender
        ON sender.id = dm.sender_id
    LEFT JOIN dbo.users receiver
        ON receiver.id = dm.receiver_id
    WHERE sender.id IS NULL
       OR receiver.id IS NULL;
END

IF OBJECT_ID(N'dbo.group_messages', N'U') IS NOT NULL
BEGIN
    SELECT
        'group_messages_orphan_sender_or_group' AS check_name,
        COUNT_BIG(*) AS orphan_count
    FROM dbo.group_messages gm
    LEFT JOIN dbo.users sender
        ON sender.id = gm.sender_id
    LEFT JOIN dbo.groups g
        ON g.group_id = gm.group_id
    WHERE sender.id IS NULL
       OR g.group_id IS NULL;
END


---------------------------------------------------------------
-- 5.7 GROUP ADMIN CHECK
-- Group không có ADMIN sẽ được hiển thị để kiểm tra.
---------------------------------------------------------------

PRINT '';
PRINT '=== 5.7 GROUP ADMIN CHECK ===';

IF OBJECT_ID(N'dbo.groups', N'U') IS NOT NULL
AND OBJECT_ID(N'dbo.group_members', N'U') IS NOT NULL
BEGIN
    SELECT
        g.group_id,
        g.group_name,

        SUM(
            CASE
                WHEN gm.role = 'ADMIN' THEN 1
                ELSE 0
            END
        ) AS admin_count,

        CASE
            WHEN SUM(
                CASE
                    WHEN gm.role = 'ADMIN' THEN 1
                    ELSE 0
                END
            ) > 0
                THEN '[PASS]'
            ELSE '[FAIL]'
        END AS verification_status

    FROM dbo.groups g
    LEFT JOIN dbo.group_members gm
        ON gm.group_id = g.group_id
    GROUP BY
        g.group_id,
        g.group_name
    ORDER BY g.group_id;
END


---------------------------------------------------------------
-- 5.8 SAMPLE DIRECT MESSAGE HISTORY
---------------------------------------------------------------

PRINT '';
PRINT '=== 5.8 SAMPLE DIRECT MESSAGE HISTORY ===';

IF OBJECT_ID(N'dbo.direct_messages', N'U') IS NOT NULL
BEGIN
    SELECT TOP (5)
        message_id,
        sender_id,
        receiver_id,
        LEFT(content, 80) AS content_preview,
        message_type,
        status,
        sent_at
    FROM dbo.direct_messages
    ORDER BY sent_at DESC, message_id DESC;
END


---------------------------------------------------------------
-- 5.9 SAMPLE GROUP MESSAGE HISTORY
---------------------------------------------------------------

PRINT '';
PRINT '=== 5.9 SAMPLE GROUP MESSAGE HISTORY ===';

IF OBJECT_ID(N'dbo.group_messages', N'U') IS NOT NULL
BEGIN
    SELECT TOP (5)
        message_id,
        sender_id,
        group_id,
        LEFT(content, 80) AS content_preview,
        message_type,
        sent_at
    FROM dbo.group_messages
    ORDER BY sent_at DESC, message_id DESC;
END
GO


-- ============================================================
-- 6. ROLLBACK SCRIPT - TÙY CHỌN KHÔI PHỤC
-- ============================================================

/*
    CẢNH BÁO:

    Rollback Phase 4 sẽ xóa:
        dbo.group_messages
        dbo.direct_messages
        dbo.group_members
        dbo.groups

    Không xóa:
        dbo.users
        dbo.email_otp

    Mặc định @ExecuteRollback = 0.

    Chỉ đổi thành 1 khi thực sự muốn xóa toàn bộ bảng Phase 4.
*/

PRINT '';
PRINT '============================================================';
PRINT '6. OPTIONAL PHASE 4 ROLLBACK';
PRINT '============================================================';
GO

DECLARE @ExecuteRollback BIT = 0;

IF @ExecuteRollback = 0
BEGIN
    PRINT 'Rollback đang OFF. Không có bảng nào bị xóa.';
END
ELSE
BEGIN
    PRINT 'Rollback đang ON. Chuẩn bị xóa các bảng Phase 4...';

    BEGIN TRY
        BEGIN TRANSACTION;

        IF OBJECT_ID(N'dbo.group_messages', N'U') IS NOT NULL
            DROP TABLE dbo.group_messages;

        IF OBJECT_ID(N'dbo.direct_messages', N'U') IS NOT NULL
            DROP TABLE dbo.direct_messages;

        IF OBJECT_ID(N'dbo.group_members', N'U') IS NOT NULL
            DROP TABLE dbo.group_members;

        IF OBJECT_ID(N'dbo.groups', N'U') IS NOT NULL
            DROP TABLE dbo.groups;

        COMMIT TRANSACTION;

        PRINT 'Phase 4 rollback completed.';
        PRINT 'users và email_otp vẫn được giữ nguyên.';
    END TRY
    BEGIN CATCH

        IF @@TRANCOUNT > 0
            ROLLBACK TRANSACTION;

        SELECT
            ERROR_NUMBER() AS error_number,
            ERROR_MESSAGE() AS error_message;

        THROW;
    END CATCH
END
GO
