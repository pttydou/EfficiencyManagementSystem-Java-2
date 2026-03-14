package com.efficiency.service;

import com.efficiency.model.ProcessInfo;

import javax.swing.Timer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 进程监控器
 * 对应原C++ ProcessMonitor类
 * 使用系统命令获取进程信息（跨平台：Windows用tasklist/wmic，macOS/Linux用ps）
 */
public class ProcessMonitor {
    private Timer gameTimer;
    private Timer allProcessTimer;
    private List<String> gameProcessNames = new ArrayList<>();
    private List<ProcessInfo> processList = new CopyOnWriteArrayList<>();

    // 回调接口（替代Qt信号槽）
    private Consumer<ProcessInfo> onGameProcessDetected;
    private Consumer<List<ProcessInfo>> onAllProcessUpdated;
    private Consumer<Boolean> onMonitorStateChanged;

    public ProcessMonitor() {
        gameTimer = new Timer(2000, e -> checkProcesses());
        allProcessTimer = new Timer(5000, e -> refreshAllProcessInfo());
    }

    public void setGameProcessNames(List<String> processNames) {
        this.gameProcessNames = new ArrayList<>(processNames);
        System.out.println("[ProcessMonitor] 已设置监控进程列表: " + gameProcessNames);
    }

    public List<String> getGameProcessNames() {
        return Collections.unmodifiableList(gameProcessNames);
    }

    public void setOnGameProcessDetected(Consumer<ProcessInfo> callback) {
        this.onGameProcessDetected = callback;
    }

    public void setOnAllProcessUpdated(Consumer<List<ProcessInfo>> callback) {
        this.onAllProcessUpdated = callback;
    }

    public void setOnMonitorStateChanged(Consumer<Boolean> callback) {
        this.onMonitorStateChanged = callback;
    }

    public void startMonitor(int gameCheckInterval, int allProcessInterval) {
        gameTimer.setDelay(gameCheckInterval);
        allProcessTimer.setDelay(allProcessInterval);
        gameTimer.start();
        allProcessTimer.start();
        System.out.println("[ProcessMonitor] 监控已启动，游戏进程检测间隔: " + gameCheckInterval
                + "ms，全进程刷新间隔: " + allProcessInterval + "ms");
        if (onMonitorStateChanged != null) onMonitorStateChanged.accept(true);
    }

    public void startMonitor(int gameCheckInterval) {
        startMonitor(gameCheckInterval, 5000);
    }

    public void stopMonitor() {
        gameTimer.stop();
        allProcessTimer.stop();
        System.out.println("[ProcessMonitor] 监控已停止");
        if (onMonitorStateChanged != null) onMonitorStateChanged.accept(false);
    }

    /**
     * 刷新所有进程信息
     * Windows: 使用wmic获取进程名和CPU
     * macOS/Linux: 使用ps命令
     */
    public void refreshAllProcessInfo() {
        List<ProcessInfo> newList = new ArrayList<>();
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("win")) {
                // Windows: wmic path win32_process get ProcessId,Name,PercentProcessorTime
                Process proc = Runtime.getRuntime().exec(new String[]{
                        "tasklist", "/FO", "CSV", "/NH"
                });
                BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), "GBK"));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.replace("\"", "");
                    String[] parts = line.split(",");
                    if (parts.length >= 2) {
                        ProcessInfo info = new ProcessInfo();
                        info.setProcessName(parts[0].trim());
                        try {
                            info.setPid(Long.parseLong(parts[1].trim()));
                        } catch (NumberFormatException ignored) {}
                        info.setCpuUsage(0.0);
                        newList.add(info);
                    }
                }
                reader.close();
                proc.waitFor();

                // 用wmic获取CPU使用率
                Process cpuProc = Runtime.getRuntime().exec(new String[]{
                        "wmic", "path", "win32_perfformatteddata_perfproc_process",
                        "get", "Name,PercentProcessorTime", "/format:csv"
                });
                BufferedReader cpuReader = new BufferedReader(
                        new InputStreamReader(cpuProc.getInputStream(), "GBK"));
                Map<String, Double> cpuMap = new HashMap<>();
                String cpuLine;
                while ((cpuLine = cpuReader.readLine()) != null) {
                    cpuLine = cpuLine.trim();
                    if (cpuLine.isEmpty()) continue;
                    String[] cpuParts = cpuLine.split(",");
                    if (cpuParts.length >= 3) {
                        String name = cpuParts[1].trim();
                        try {
                            double cpu = Double.parseDouble(cpuParts[2].trim());
                            cpuMap.put(name.toLowerCase(), cpu);
                        } catch (NumberFormatException ignored) {}
                    }
                }
                cpuReader.close();
                cpuProc.waitFor();

                for (ProcessInfo info : newList) {
                    String nameKey = info.getProcessName().replace(".exe", "").toLowerCase();
                    if (cpuMap.containsKey(nameKey)) {
                        info.setCpuUsage(Math.min(100.0, cpuMap.get(nameKey)));
                    }
                }
            } else {
                // macOS/Linux: ps aux
                Process proc = Runtime.getRuntime().exec(new String[]{"ps", "aux"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                String line;
                boolean firstLine = true;
                while ((line = reader.readLine()) != null) {
                    if (firstLine) { firstLine = false; continue; } // 跳过标题行
                    String[] parts = line.trim().split("\\s+", 11);
                    if (parts.length >= 11) {
                        ProcessInfo info = new ProcessInfo();
                        try {
                            info.setPid(Long.parseLong(parts[1]));
                            info.setCpuUsage(Math.min(100.0, Double.parseDouble(parts[2])));
                        } catch (NumberFormatException ignored) {}
                        info.setProcessName(parts[10]);
                        newList.add(info);
                    }
                }
                reader.close();
                proc.waitFor();
            }
        } catch (Exception e) {
            System.err.println("[ProcessMonitor] 进程信息获取失败: " + e.getMessage());
        }

        processList = new CopyOnWriteArrayList<>(newList);
        if (onAllProcessUpdated != null) {
            onAllProcessUpdated.accept(Collections.unmodifiableList(newList));
        }
    }

    /**
     * 检测违规游戏进程
     */
    private void checkProcesses() {
        for (ProcessInfo info : processList) {
            String name = info.getProcessName();
            for (String gameName : gameProcessNames) {
                if (name.equalsIgnoreCase(gameName)) {
                    System.out.println("[ProcessMonitor] 检测到违规进程: " + name);
                    if (onGameProcessDetected != null) {
                        onGameProcessDetected.accept(info);
                    }
                    return; // 检测到一个就返回
                }
            }
        }
    }

    public List<ProcessInfo> getAllProcessWithCpuUsage() {
        refreshAllProcessInfo();
        return Collections.unmodifiableList(processList);
    }
}
