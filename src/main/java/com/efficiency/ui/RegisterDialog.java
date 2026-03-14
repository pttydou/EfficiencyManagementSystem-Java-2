package com.efficiency.ui;

import com.efficiency.service.AuthClient;
import com.efficiency.service.EmailSender;

import javax.swing.*;
import java.awt.*;

/**
 * 注册对话框
 * 对应原C++ RegisterDialog类
 */
public class RegisterDialog extends JDialog {
    private JTextField emailEdit;
    private JTextField usernameEdit;
    private JPasswordField passwordEdit;
    private JPasswordField confirmPwdEdit;
    private JTextField verificationCodeEdit;
    private JButton getCodeBtn;
    private JButton registerBtn;

    private final AuthClient authClient;
    private final EmailSender emailSender;
    private String email;
    private Timer codeTimer;
    private int remainingTime;

    public RegisterDialog(AuthClient authClient, EmailSender emailSender, Dialog parent) {
        super(parent, "注册", true);
        this.authClient = authClient;
        this.emailSender = emailSender;
        initUI();
        connectSignals();
        setSize(350, 320);
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    private void initUI() {
        setLayout(null);

        JLabel emailLabel = new JLabel("邮箱:");
        emailLabel.setBounds(30, 20, 50, 25);
        add(emailLabel);
        emailEdit = new JTextField();
        emailEdit.setBounds(90, 20, 130, 25);
        add(emailEdit);
        getCodeBtn = new JButton("获取验证码");
        getCodeBtn.setBounds(225, 20, 100, 25);
        add(getCodeBtn);

        JLabel userLabel = new JLabel("用户名:");
        userLabel.setBounds(30, 60, 50, 25);
        add(userLabel);
        usernameEdit = new JTextField();
        usernameEdit.setBounds(90, 60, 130, 25);
        add(usernameEdit);

        JLabel pwdLabel = new JLabel("密码:");
        pwdLabel.setBounds(30, 100, 50, 25);
        add(pwdLabel);
        passwordEdit = new JPasswordField();
        passwordEdit.setBounds(90, 100, 130, 25);
        add(passwordEdit);

        JLabel confirmLabel = new JLabel("确认密码:");
        confirmLabel.setBounds(20, 140, 65, 25);
        add(confirmLabel);
        confirmPwdEdit = new JPasswordField();
        confirmPwdEdit.setBounds(90, 140, 130, 25);
        add(confirmPwdEdit);

        JLabel codeLabel = new JLabel("验证码:");
        codeLabel.setBounds(30, 180, 50, 25);
        add(codeLabel);
        verificationCodeEdit = new JTextField();
        verificationCodeEdit.setBounds(90, 180, 130, 25);
        add(verificationCodeEdit);

        registerBtn = new JButton("注册");
        registerBtn.setBounds(120, 230, 100, 30);
        add(registerBtn);
    }

    private void connectSignals() {
        getCodeBtn.addActionListener(e -> onGetCodeClicked());
        registerBtn.addActionListener(e -> onRegisterClicked());

        // 验证码发送回调
        authClient.setOnCodeSent((success, msg) -> {
            SwingUtilities.invokeLater(() -> {
                if (!success) {
                    JOptionPane.showMessageDialog(this, "验证码请求失败：" + msg, "失败", JOptionPane.WARNING_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "验证码请求已发送", "成功", JOptionPane.INFORMATION_MESSAGE);
                }
            });
        });

        // 注册结果回调
        authClient.setOnRegisterResult((success, message) -> {
            SwingUtilities.invokeLater(() -> {
                if (success) {
                    JOptionPane.showMessageDialog(this, message, "注册成功", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, message, "注册失败", JOptionPane.WARNING_MESSAGE);
                }
            });
        });

        emailSender.setOnSendSuccess(() -> {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, "验证码已发送至邮箱，请查收", "提示", JOptionPane.INFORMATION_MESSAGE));
        });

        emailSender.setOnSendFailed(error -> {
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, error, "邮件发送失败", JOptionPane.WARNING_MESSAGE));
        });
    }

    private void onGetCodeClicked() {
        email = emailEdit.getText().trim();
        if (email.isEmpty() || !email.contains("@") || !email.contains(".")) {
            JOptionPane.showMessageDialog(this, "请输入有效邮箱地址", "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 生成验证码并发送邮件
        authClient.requestVerificationCode(email);
        String code = authClient.getVerificationCode(email);
        if (code != null) {
            emailSender.sendVerificationCode(email, code);
        }

        // 倒计时60秒
        remainingTime = 60;
        getCodeBtn.setEnabled(false);
        getCodeBtn.setText("倒计时 60 秒");
        codeTimer = new Timer(1000, e -> updateCodeTimer());
        codeTimer.start();
    }

    private void updateCodeTimer() {
        remainingTime--;
        if (remainingTime <= 0) {
            getCodeBtn.setEnabled(true);
            getCodeBtn.setText("获取验证码");
            codeTimer.stop();
        } else {
            getCodeBtn.setText("倒计时 " + remainingTime + " 秒");
        }
    }

    private void onRegisterClicked() {
        String password = new String(passwordEdit.getPassword());
        String confirmPwd = new String(confirmPwdEdit.getPassword());
        String code = verificationCodeEdit.getText().trim();

        if (password.isEmpty() || confirmPwd.isEmpty() || code.isEmpty() || code.length() != 6) {
            JOptionPane.showMessageDialog(this, "请填写完整信息，验证码需为6位数字", "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!password.equals(confirmPwd)) {
            JOptionPane.showMessageDialog(this, "两次密码不一致", "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        authClient.registerUser(email, password, code);
    }
}
