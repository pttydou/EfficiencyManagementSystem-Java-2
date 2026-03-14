package com.efficiency.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * 认证客户端
 * 对应原C++ AuthClient类
 * 单机版直接调用本地UserManager，不走HTTP
 * 验证码存在内存Map中（替代原来的Redis）
 */
public class AuthClient {
    private final UserManager userManager;
    // 内存存储验证码：email -> {code, expireTime}
    private final Map<String, CodeEntry> verificationCodes = new ConcurrentHashMap<>();

    // 回调
    private BiConsumer<Boolean, String> onCodeSent;
    private BiConsumer<Boolean, String> onRegisterResult;
    private BiConsumer<Boolean, String> onLoginResult;

    private static class CodeEntry {
        String code;
        long expireTime;
        CodeEntry(String code, long expireTime) {
            this.code = code;
            this.expireTime = expireTime;
        }
    }

    public AuthClient() {
        this.userManager = new UserManager();
    }

    public void setOnCodeSent(BiConsumer<Boolean, String> callback) {
        this.onCodeSent = callback;
    }

    public void setOnRegisterResult(BiConsumer<Boolean, String> callback) {
        this.onRegisterResult = callback;
    }

    public void setOnLoginResult(BiConsumer<Boolean, String> callback) {
        this.onLoginResult = callback;
    }

    /**
     * 生成并存储验证码（5分钟有效）
     */
    public void requestVerificationCode(String email) {
        if (email == null || email.isEmpty()) {
            if (onCodeSent != null) onCodeSent.accept(false, "请提供邮箱");
            return;
        }
        String code = String.valueOf((int) (Math.random() * 900000) + 100000);
        verificationCodes.put(email, new CodeEntry(code, System.currentTimeMillis() + 300_000));
        System.out.println("已生成验证码 " + code + " 并存储，邮箱: " + email);
        if (onCodeSent != null) onCodeSent.accept(true, "验证码已生成");
    }

    /**
     * 获取验证码（供EmailSender使用）
     */
    public String getVerificationCode(String email) {
        CodeEntry entry = verificationCodes.get(email);
        if (entry != null && System.currentTimeMillis() < entry.expireTime) {
            return entry.code;
        }
        return null;
    }

    /**
     * 注册用户
     */
    public void registerUser(String email, String password, String code) {
        if (email == null || password == null || code == null ||
                email.isEmpty() || password.isEmpty() || code.isEmpty()) {
            if (onRegisterResult != null) onRegisterResult.accept(false, "参数不全");
            return;
        }

        // 验证验证码
        CodeEntry entry = verificationCodes.get(email);
        if (entry == null || System.currentTimeMillis() >= entry.expireTime || !entry.code.equals(code)) {
            if (onRegisterResult != null) onRegisterResult.accept(false, "验证码无效或已过期");
            return;
        }

        // 注册
        String error = userManager.registerUser(email, password);
        if (error == null) {
            verificationCodes.remove(email);
            if (onRegisterResult != null) onRegisterResult.accept(true, "注册成功");
        } else {
            if (onRegisterResult != null) onRegisterResult.accept(false, error);
        }
    }

    /**
     * 登录
     */
    public void loginUser(String email, String password) {
        if (email == null || password == null || email.isEmpty() || password.isEmpty()) {
            if (onLoginResult != null) onLoginResult.accept(false, "参数不全");
            return;
        }

        String error = userManager.loginUser(email, password);
        if (error == null) {
            if (onLoginResult != null) onLoginResult.accept(true, "登录成功");
        } else {
            if (onLoginResult != null) onLoginResult.accept(false, error);
        }
    }
}
