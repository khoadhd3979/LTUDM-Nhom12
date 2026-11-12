/*
    ============================================================
    CẢNH BÁO CỰC KỲ QUAN TRỌNG
    ============================================================

    Script này, KHI @CONFIRM_DROP = 1, sẽ XÓA TOÀN BỘ database
    SecureChatDB, bao gồm:
        - users
        - email_otp
        - groups
        - group_members
        - direct_messages
        - group_messages

    TẤT CẢ DỮ LIỆU SẼ MẤT VĨNH VIỄN.

    Chỉ chạy script này khi:
        - Đây là môi trường dev/test.
        - Bạn đã backup hoặc không cần giữ dữ liệu.
        - Bạn thực sự muốn reset database.

    Mặc định @CONFIRM_DROP = 0.
    Script sẽ KHÔNG làm gì nếu @CONFIRM_DROP = 0.
*/
SET NOCOUNT ON;
GO

USE master;
GO

DECLARE @CONFIRM_DROP BIT = 0;  -- Đổi thành 1 để thực sự DROP DATABASE

IF @CONFIRM_DROP = 0
BEGIN
    PRINT 'Reset database OFF (CONFIRM_DROP = 0). Không thực hiện thao tác xóa nào.';
    RETURN;
END

PRINT 'Reset database ON. Chuẩn bị DROP DATABASE SecureChatDB...';

IF DB_ID(N'SecureChatDB') IS NULL
BEGIN
    PRINT 'Database SecureChatDB không tồn tại — không có gì để drop.';
    RETURN;
END

BEGIN TRY
    -- Force disconnect any open session before drop
    ALTER DATABASE SecureChatDB SET SINGLE_USER WITH ROLLBACK IMMEDIATE;

    DROP DATABASE SecureChatDB;

    PRINT 'Đã drop database SecureChatDB thành công.';
END TRY
BEGIN CATCH
    PRINT 'DROP DATABASE thất bại.';
    SELECT
        ERROR_NUMBER()  AS error_number,
        ERROR_MESSAGE() AS error_message;

    -- Cố gắng đưa database trở lại trạng thái multi-user nếu vẫn còn
    BEGIN TRY
        ALTER DATABASE SecureChatDB SET MULTI_USER;
    END TRY
    BEGIN CATCH
        -- Bỏ qua: database có thể đã bị xóa một phần
    END CATCH

    ;THROW;
END CATCH
GO