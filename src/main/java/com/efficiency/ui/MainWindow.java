package com.efficiency.ui;

import com.efficiency.model.ProcessInfo;
import com.efficiency.service.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 主窗口
 * 对应原C++ MainWindow类
 */
public class MainWindow extends JFrame {
    // 左侧控件
    private JButton startBtn;
    private JButton stopBtn;
    private JLabel statusLabel;
    private JLabel markStatusLabel;
    private JTable captureTable;
    private DefaultTableModel captureTableModel;

    // 右侧控件
    private JButton markAsGameBtn;
    private JButton openManagerBtn;
    private JComboBox<String> processSelectionCombo;
    private JTextField processInputEdit;
    private JTable allProcessTable;
    private DefaultTableModel allProcessTableModel;

    // 右上角
    private JButton loginBtn;

    // 服务层
    private ProcessMonitor processMonitor;
    private ScreenCapture screenCapture;
    private MarkedGameManager markedGameManager;
    private AuthClient authClient;
    private EmailSender emailSender;

    public MainWindow() {
        super("效率管理系统");
        initServices();
        initUI();
        connectSignals();
        loadMarkedGames();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(750, 560);
        setLocationRelativeTo(null);
    }

    private void initServices() {
        processMonitor = new ProcessMonitor();
        screenCapture = new ScreenCapture();
        markedGameManager = new MarkedGameManager();
        authClient = new AuthClient();
        emailSender = new EmailSender();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(null);

        // === 左侧区域 ===
        startBtn = new JButton("开始监控");
        startBtn.setBounds(40, 140, 90, 28);
        mainPanel.add(startBtn);

        stopBtn = new JButton("停止监控");
        stopBtn.setBounds(135, 140, 90, 28);
        mainPanel.add(stopBtn);

        statusLabel = new JLabel("监控状态");
        statusLabel.setBounds(235, 140, 120, 28);
        mainPanel.add(statusLabel);

        markStatusLabel = new JLabel("TextLabel");
        markStatusLabel.setBounds(30, 210, 310, 25);
        mainPanel.add(markStatusLabel);

        // 截屏记录表格
        String[] captureColumns = {"时间", "进程名", "截图路径"};
        captureTableModel = new DefaultTableModel(captureColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        captureTable = new JTable(captureTableModel);
        JScrollPane captureScroll = new JScrollPane(captureTable);
        captureScroll.setBounds(30, 240, 310, 200);
        mainPanel.add(captureScroll);

        // === 右侧区域 ===
        markAsGameBtn = new JButton("标记为游戏");
        markAsGameBtn.setBounds(350, 140, 100, 28);
        mainPanel.add(markAsGameBtn);

        openManagerBtn = new JButton("管理标记进程");
        openManagerBtn.setBounds(455, 140, 110, 28);
        mainPanel.add(openManagerBtn);

        processSelectionCombo = new JComboBox<>();
        processSelectionCombo.setBounds(350, 175, 360, 25);
        mainPanel.add(processSelectionCombo);

        processInputEdit = new JTextField();
        processInputEdit.setBounds(350, 205, 360, 25);
        processInputEdit.setToolTipText("手动输入进程名");
        mainPanel.add(processInputEdit);

        // 全部进程表格
        String[] processColumns = {"进程名", "CPU占用（%）"};
        allProcessTableModel = new DefaultTableModel(processColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        allProcessTable = new JTable(allProcessTableModel);
        JScrollPane processScroll = new JScrollPane(allProcessTable);
        processScroll.setBounds(350, 240, 360, 200);
        mainPanel.add(processScroll);

        // === 右上角登录按钮 ===
        loginBtn = new JButton("Log in");
        loginBtn.setBounds(630, 20, 80, 28);
        mainPanel.add(loginBtn);

        setContentPane(mainPanel);
    }

    private void connectSignals() {
        // 进程监控回调
        processMonitor.setOnGameProcessDetected(info -> {
            SwingUtilities.invokeLater(() -> screenCapture.captureScreen(info.getProcessName()));
        });

        processMonitor.setOnAllProcessUpdated(processList -> {
            SwingUtilities.invokeLater(() -> {
                onAllProcessUpdated(processList);
                refreshProcessSelectionCombo(processList);
            });
        });

        // 截屏完成回调（4个参数：进程名、路径、是否成功、错误信息）
        screenCapture.setOnCaptureFinished((processName, path, success, errorMsg) -> {
            SwingUtilities.invokeLater(() -> onCaptureFinished(processName, path, success, errorMsg));
        });

        // 按钮事件
        startBtn.addActionListener(e -> onStartMonitorClicked());
        stopBtn.addActionListener(e -> onStopMonitorClicked());
        markAsGameBtn.addActionListener(e -> onMarkAsGameBtnClicked());

        openManagerBtn.addActionListener(e -> {
            MarkedProcessManagerDialog dialog = new MarkedProcessManagerDialog(markedGameManager, this);
            dialog.setOnMarkedProcessUpdated(() -> {
                List<String> updatedGames = markedGameManager.getAllProcessNames();
                processMonitor.setGameProcessNames(updatedGames);
                markStatusLabel.setText("已加载 " + updatedGames.size() + " 个标记游戏进程");
            });
            dialog.setVisible(true);
        });

        loginBtn.addActionListener(e -> {
            LoginDialog loginDialog = new LoginDialog(authClient, emailSender, this);
            loginDialog.setOnLoginSuccessfully(() -> loginBtn.setVisible(false));
            loginDialog.setVisible(true);
        });
    }

    private void loadMarkedGames() {
        List<String> markedGames = markedGameManager.getAllProcessNames();
        if (!markedGames.isEmpty()) {
            processMonitor.setGameProcessNames(markedGames);
            markStatusLabel.setText("已加载 " + markedGames.size() + " 个标记游戏进程");
        } else {
            markStatusLabel.setText("暂无标记的游戏进程，可手动标记");
        }
    }

    // === 槽函数对应方法 ===

    private void onStartMonitorClicked() {
        processMonitor.startMonitor(2000, 5000);
        statusLabel.setText("监控状态：运行中");
        statusLabel.setForeground(new Color(0, 128, 0));
    }

    private void onStopMonitorClicked() {
        processMonitor.stopMonitor();
        statusLabel.setText("监控状态：已停止");
        statusLabel.setForeground(Color.RED);
    }

    private void onCaptureFinished(String processName, String screenshotPath, boolean success, String errorMsg) {
        if (!success) {
            System.out.println("[MainWindow] 截屏失败：" + errorMsg);
            return;
        }

        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        captureTableModel.addRow(new Object[]{currentTime, processName, screenshotPath});
    }

    private void onAllProcessUpdated(List<ProcessInfo> processList) {
        allProcessTableModel.setRowCount(0);
        for (ProcessInfo info : processList) {
            String cpuText = String.format("%.1f%%", info.getCpuUsage());
            allProcessTableModel.addRow(new Object[]{info.getProcessName(), cpuText});
        }
    }

    private void refreshProcessSelectionCombo(List<ProcessInfo> processList) {
        String lastSelected = (String) processSelectionCombo.getSelectedItem();

        processSelectionCombo.removeAllItems();
        Set<String> uniqueNames = new LinkedHashSet<>();
        for (ProcessInfo info : processList) {
            uniqueNames.add(info.getProcessName());
        }
        for (String name : uniqueNames) {
            processSelectionCombo.addItem(name);
        }

        if (lastSelected != null && uniqueNames.contains(lastSelected)) {
            processSelectionCombo.setSelectedItem(lastSelected);
        }
    }

    private void onMarkAsGameBtnClicked() {
        String processName = processInputEdit.getText().trim();
        if (processName.isEmpty()) {
            processName = (String) processSelectionCombo.getSelectedItem();
        }

        if (processName == null || processName.isEmpty()) {
            markStatusLabel.setText("请在输入框输入进程名，或从下拉框选择！");
            markStatusLabel.setForeground(new Color(255, 165, 0));
            return;
        }

        String description = "默认描述";
        boolean autoMonitor = true;

        boolean success = markedGameManager.addMarkedGame(processName, description, autoMonitor);
        if (success) {
            List<String> updatedGames = markedGameManager.getAllProcessNames();
            processMonitor.setGameProcessNames(updatedGames);
            markStatusLabel.setText("✅ 成功标记「" + processName + "」为游戏进程");
            markStatusLabel.setForeground(new Color(0, 128, 0));
            processInputEdit.setText("");
        } else {
            markStatusLabel.setText("❌ 「" + processName + "」已被标记为游戏进程，无需重复操作");
            markStatusLabel.setForeground(Color.RED);
        }
    }
}
