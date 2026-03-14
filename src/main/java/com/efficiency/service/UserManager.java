package com.efficiency.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

/**
 * 用户管理器 - 本地SQLite用户管理
 * 对应原C++ UserManager类
 */
public class UserManager {
    private Connection connection;

    public UserManager() {
        initDatabase();
    }

    public boolean initDatabase() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:user_credentials.db");
            Statement stmt = connection.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "username TEXT UNIQUE NOT NULL,"
                    + "password_hash TEXT NOT NULL)");
            stmt.close();
            return true;
        } catch (SQLException e) {
            System.err.println("数据库打开失败：" + e.getMessage());
            return false;
        }
    }

    public String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256算法不可用", e);
        }
    }

    public String registerUser(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return "用户名或密码不能为空";
        }
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (username, password_hash) VALUES (?, ?)");
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            ps.executeUpdate();
            ps.close();
            return null; // null表示成功
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                return "用户名已存在";
            }
            return "注册失败：" + e.getMessage();
        }
    }

    public String loginUser(String username, String password) {
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return "用户名或密码不能为空";
        }
        try {
            PreparedStatement ps = connection.prepareStatement(
                    "SELECT password_hash FROM users WHERE username = ?");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                rs.close(); ps.close();
                return "用户名不存在";
            }
            String storedHash = rs.getString("password_hash");
            rs.close(); ps.close();
            if (!storedHash.equals(hashPassword(password))) {
                return "密码错误";
            }
            return null; // null表示成功
        } catch (SQLException e) {
            return "查询用户失败：" + e.getMessage();
        }
    }

    public boolean isDatabaseOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("关闭数据库失败：" + e.getMessage());
        }
    }
}
