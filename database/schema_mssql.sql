-- SecureChat-Java — SQL Server schema (Phase 1)
-- Chạy trong SSMS với quyền tạo DB.

IF DB_ID('SecureChatDB') IS NULL
    CREATE DATABASE SecureChatDB;
GO
USE SecureChatDB;
GO

-- Users: email là username, trạng thái tài khoản phục vụ OTP
IF OBJECT_ID('dbo.users','U') IS NULL
CREATE TABLE dbo.users (
    id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    email          NVARCHAR(255) NOT NULL UNIQUE,
    password_hash  NVARCHAR(100) NOT NULL,
    full_name      NVARCHAR(120) NOT NULL,
    gender         NVARCHAR(10)  NULL
        CHECK (gender IN ('MALE','FEMALE','OTHER')),
    date_of_birth  DATE          NULL,
    status         NVARCHAR(10)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','ACTIVE','LOCKED')),
    created_at     DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    updated_at     DATETIME2(3)  NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

-- OTP xác thực email: hash OTP, không lưu plain
IF OBJECT_ID('dbo.email_otp','U') IS NULL
CREATE TABLE dbo.email_otp (
    id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    otp_hash       NVARCHAR(100) NOT NULL,
    purpose        NVARCHAR(10) NOT NULL
        CHECK (purpose IN ('REGISTER','RESET')),
    expires_at     DATETIME2(3) NOT NULL,
    attempt_count  INT          NOT NULL DEFAULT 0,
    consumed_at    DATETIME2(3) NULL,
    created_at     DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT fk_otp_user FOREIGN KEY (user_id) REFERENCES dbo.users(id) ON DELETE CASCADE
);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='ix_otp_user_purpose')
    CREATE INDEX ix_otp_user_purpose
        ON dbo.email_otp(user_id, purpose, expires_at);
GO