package com.efficiency.ui;

import com.efficiency.model.MarkedGame;
import com.efficiency.service.MarkedGameManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 标记进程管理对话框
 * 对应原C++ MarkedProcessManagerDialog类
 */
public class MarkedProcessManagerDialog extends JDialog {
    private JList<String> processListWidget;
    private DefaultListModel<String> listModel;
    private JLabel emptyTipLabel;
    private JButton deleteBtn;
    private JButton refreshBtn;
    private JButton closeBtn;

    private final MarkedGameManager markedManager;
    private Runnable onMarkedProcessUpdated;

    public MarkedProcessManagerDialog(MarkedGameManager manager, Frame parent) {
        super(parent, "标记进程管理", true);
        this.markedManager = manager;
        initUI();
        connectSignals();
        loadMarkedProcesses();
        setSize(600, 400);
        setLocationRelativeTo(parent);
        setResizable(false);
    }

    public void setOnMarkedProcessUpdated(Runnable callback) {
        this.onMarkedProcessUpdated = callback;
    }

    private void initUI() {
        setLayout(null);

        listModel = new DefaultListModel<>();
        processListWidget = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(processListWidget);
        scrollPane.setBounds(20, 20, 550, 260);
        add(scrollPane);

        emptyTipLabel = new JLabel("暂无标记的游戏进程");
        emptyTipLabel.setBounds(20, 290, 200, 20);
        add(emptyTipLabel);

        deleteBtn = new JButton("删除选中进程");
        deleteBtn.setBounds(20, 320, 120, 30);
        add(deleteBtn);

        refreshBtn = new JButton("刷新列表");
        refreshBtn.setBounds(150, 320, 100, 30);
        add(refreshBtn);

        closeBtn = new JButton("关闭");
        closeBtn.setBounds(260, 320, 80, 30);
        add(closeBtn);
    }

    private void connectSignals() {
        deleteBtn.addActionListener(e -> onDeleteBtnClicked());
        refreshBtn.addActionListener(e -> loadMarkedProcesses());
        closeBtn.addActionListener(e -> dispose());
    }

    private void loadMarkedProcesses() {
        listModel.clear();
        List<MarkedGame> markedProcesses = markedManager.loadMarkedGames();

        if (markedProcesses.isEmpty()) {
            emptyTipLabel.setVisible(true);
            processListWidget.setVisible(false);
            deleteBtn.setEnabled(false);
        } else {
            emptyTipLabel.setVisible(false);
            processListWidget.setVisible(true);
            deleteBtn.setEnabled(true);
            for (MarkedGame game : markedProcesses) {
                String displayText = String.format(
                        "进程名: %s | 标记时间: %s | 描述: %s | 自动监控: %s",
                        game.getProcessName(),
                        game.getMarkedTime(),
                        game.getDescription(),
                        game.isAutoMonitor() ? "是" : "否"
                );
                listModel.addElement(displayText);
            }
        }
    }

    private void onDeleteBtnClicked() {
        String selected = processListWidget.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "请先选中要删除的进程！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 从显示文本中提取processName（格式："进程名: xxx | ..."）
        String processName = selected.split("\\|")[0].split(":")[1].trim();

        int confirm = JOptionPane.showConfirmDialog(this,
                "是否删除标记进程「" + processName + "」？",
                "确认删除", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (markedManager.removeMarkedGame(processName)) {
                JOptionPane.showMessageDialog(this, "进程删除成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
                loadMarkedProcesses();
                if (onMarkedProcessUpdated != null) onMarkedProcessUpdated.run();
            } else {
                JOptionPane.showMessageDialog(this, "进程删除失败！", "失败", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
