package com.efficiency;

import com.efficiency.ui.MainWindow;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;

/**
 * 程序入口
 * 对应原C++ main.cpp
 */
public class Main {
    public static void main(String[] args) {
        // 设置现代化外观
        try {
            FlatLightLaf.setup();
        } catch (Exception e) {
            System.err.println("FlatLaf初始化失败，使用默认外观");
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            window.setVisible(true);
        });
    }
}
