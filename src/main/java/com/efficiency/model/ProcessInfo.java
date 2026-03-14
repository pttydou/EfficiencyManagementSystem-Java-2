package com.efficiency.model;

import java.time.LocalDateTime;

/**
 * 进程信息实体类
 * 对应原C++ ProcessInfo类
 */
public class ProcessInfo {
    private String processName;
    private double cpuUsage;
    private long pid;
    private LocalDateTime updateTime;

    public ProcessInfo() {}

    public ProcessInfo(String processName, double cpuUsage, long pid) {
        this.processName = processName;
        this.cpuUsage = cpuUsage;
        this.pid = pid;
        this.updateTime = LocalDateTime.now();
    }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }
    public double getCpuUsage() { return cpuUsage; }
    public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }
    public long getPid() { return pid; }
    public void setPid(long pid) { this.pid = pid; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
