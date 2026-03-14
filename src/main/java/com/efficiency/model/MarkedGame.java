package com.efficiency.model;

/**
 * 标记的游戏进程实体类
 */
public class MarkedGame {
    private String processName;
    private String markedTime;
    private String description;
    private boolean autoMonitor;

    public MarkedGame() {}

    public MarkedGame(String processName, String description, boolean autoMonitor, String markedTime) {
        this.processName = processName;
        this.description = description;
        this.autoMonitor = autoMonitor;
        this.markedTime = markedTime;
    }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public String getMarkedTime() { return markedTime; }
    public void setMarkedTime(String markedTime) { this.markedTime = markedTime; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isAutoMonitor() { return autoMonitor; }
    public void setAutoMonitor(boolean autoMonitor) { this.autoMonitor = autoMonitor; }
}
