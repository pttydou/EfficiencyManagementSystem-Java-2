package com.efficiency.ui;

import com.efficiency.service.AuthClient;
import com.efficiency.service.EmailSender;

import javax.swing.*;
import java.awt.*;

/**
 * 登录对话框
 * 对应原C++ LoginDialog类
 */
public class LoginDialog extends JDialog {
    private JTextField usernameEdit;
    private JPasswordField passwordEdit;
    private JButton loginBtn;
    private JButton registerBtn;
    private JButton forgotPwdBtn;

    private final AuthClient authClient;
    private final EmailSender emailSender;
    private boolean loginSuccess = false;

    // 登录成功回调
    private Runnable onLoginSuccessfully;

    public LoginDialog(AuthClient authClient, EmailSender emailSender, Frame parent) {
        super(parent, "登录", true);
        this.authClient = authClient;
        this.emailSender = emailSender;
        initUI();
        connectSignals();
        setSize(400, 300);
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    public void setOnLoginSuccessfully(Runnable callback) {
        this.onLoginSuccessfully = callback;
    }

    private void initUI() {
        setLayout(null);

        JLabel userLabel = new JLabel("用户名:");
        userLabel.setBounds(60, 60, 60, 25);
        add(userLabel);

        usernameEdit = new JTextField();
        usernameEdit.setBounds(130, 60, 180, 25);
        add(usernameEdit);

        JLabel pwdLabel = new JLabel("密码:");
        pwdLabel.setBounds(60, 100, 60, 25);
        add(pwdLabel);

        passwordEdit = new JPasswordField();
        passwordEdit.setBounds(130, 100, 180, 25);
        add(passwordEdit);

        loginBtn = new JButton("登录");
        loginBtn.setBounds(130, 150, 80, 30);
        add(loginBtn);

        registerBtn = new JButton("注册");
        registerBtn.setBounds(220, 150, 80, 30);
        add(registerBtn);

        forgotPwdBtn = new JButton("忘记密码");
        forgotPwdBtn.setBounds(220, 190, 80, 30);
        add(forgotPwdBtn);
    }

    private void connectSignals() {
        // 登录结果回调
        authClient.setOnLoginResult((success, message) -> {
            SwingUtilities.invokeLater(() -> handleLoginResult(success, message));
        });

        loginBtn.addActionListener(e -> onLoginClicked());
        registerBtn.addActionListener(e -> onRegisterClicked());
        forgotPwdBtn.addActionListener(e -> onForgotPwdClicked());
    }

    private void onLoginClicked() {
        String username = usernameEdit.getText().trim();
        String password = new String(passwordEdit.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请填写用户名和密码", "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        authClient.loginUser(username, password);
    }

    private void handleLoginResult(boolean success, String message) {
        if (success) {
            loginSuccess = true;
            if (onLoginSuccessfully != null) onLoginSuccessfully.run();
            dispose();
        } else {
            JOptionPane.showMessageDialog(this, message, "登录失败", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void onRegisterClicked() {
        RegisterDialog regDialog = new RegisterDialog(authClient, emailSender, this);
        regDialog.setVisible(true);
    }

    private void onForgotPwdClicked() {
        JOptionPane.showMessageDialog(this, "请联系管理员重置密码", "忘记密码", JOptionPane.INFORMATION_MESSAGE);
    }

    public boolean isLoginSuccess() {
        return loginSuccess;
    }
}
