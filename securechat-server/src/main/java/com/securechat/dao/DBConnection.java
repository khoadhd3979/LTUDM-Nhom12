package com.securechat.dao;

import com.securechat.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Facade kết nối SQL Server.
 * - Singleton chỉ để giữ API cũ (getInstance()), KHÔNG cache Connection.
 * - Mỗi getConnection()/open() trả về Connection MỚI → an toàn đa luồng.
 * - Cấu hình đọc từ AppConfig (biến môi trường), không hardcode.
 */
public final class DBConnection {
    private static final DBConnection INSTANCE = new DBConnection();
    private DBConnection() {}

    public static DBConnection getInstance() { return INSTANCE; }

    /** Tương thích code cũ: conn = DBConnection.getInstance().getConnection(); */
    public Connection getConnection() throws SQLException { return open(); }

    /** Dùng trực tiếp trong DAO mới. */
    public static Connection open() throws SQLException {
        return DriverManager.getConnection(
                AppConfig.dbUrl(),
                AppConfig.dbUser(),
                AppConfig.dbPassword());
    }
}