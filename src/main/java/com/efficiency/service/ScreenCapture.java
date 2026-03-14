package com.efficiency.service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;

/**
 * 截屏工具
 * 对应原C++ ScreenCapture类
 * 使用Java AWT Robot实现截屏
 */
public class ScreenCapture {
    private String rootDir;

    // 回调：(截图路径, 是否成功)
    private CaptureCallback onCaptureFinished;

    @FunctionalInterface
    public interface CaptureCallback {
        void onFinished(String screenshotPath, boolean success, String errorMsg);
    }

    public ScreenCapture() {
        this.rootDir = System.getProperty("user.dir") + File.separator + "MonitoringScreenshots" + File.separator;
    }

    public void setScreenshotRootDir(String dir) {
        this.rootDir = dir;
    }

    public void setOnCaptureFinished(CaptureCallback callback) {
        this.onCaptureFinished = callback;
    }

    private String generateFilePath(String processName) {
        File dir = new File(rootDir);
        if (!dir.exists()) {
            if (dir.mkdirs()) {
                System.out.println("[ScreenCapture] 已创建截图存储目录: " + rootDir);
            } else {
                System.out.println("[ScreenCapture] 截图目录创建失败，使用当前目录");
                rootDir = System.getProperty("user.dir") + File.separator + "MonitoringScreenshots" + File.separator;
                new File(rootDir).mkdirs();
            }
        }

        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String fileName = String.format("Device_001_%s_%s.png", processName, timeStamp);
        return rootDir + fileName;
    }

    public void captureScreen(String processName, int delayMs) {
        if (delayMs > 0) {
            new Thread(() -> {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {}
                doCapture(processName);
            }).start();
        } else {
            doCapture(processName);
        }
    }

    public void captureScreen(String processName) {
        captureScreen(processName, 0);
    }

    private void doCapture(String processName) {
        try {
            Robot robot = new Robot();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Rectangle screenRect = new Rectangle(screenSize);
            BufferedImage screenshot = robot.createScreenCapture(screenRect);

            String filePath = generateFilePath(processName);
            File outputFile = new File(filePath);
            if (ImageIO.write(screenshot, "PNG", outputFile)) {
                System.out.println("[ScreenCapture] 截屏成功，路径: " + filePath);
                if (onCaptureFinished != null) {
                    onCaptureFinished.onFinished(filePath, true, "");
                }
            } else {
                System.out.println("[ScreenCapture] 截屏保存失败，路径: " + filePath);
                if (onCaptureFinished != null) {
                    onCaptureFinished.onFinished("", false, "截图保存失败");
                }
            }
        } catch (AWTException e) {
            System.err.println("[ScreenCapture] 无法创建Robot对象，截屏失败");
            if (onCaptureFinished != null) {
                onCaptureFinished.onFinished("", false, "无法创建截屏对象: " + e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("[ScreenCapture] 截屏异常: " + e.getMessage());
            if (onCaptureFinished != null) {
                onCaptureFinished.onFinished("", false, "截屏异常: " + e.getMessage());
            }
        }
    }
}
