package com.example.music.domain.server;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.Data;

/**
 * JVM 信息（参考若依/zzyl 监控模块）
 */
@Data
public class Jvm {

    /** 已分配堆（字节） */
    private double total;
    /** 最大堆（字节） */
    private double max;
    /** 空闲堆（字节） */
    private double free;
    /** Java 版本 */
    private String version;
    /** 安装路径 */
    private String home;

    private static final RuntimeMXBean RUNTIME = ManagementFactory.getRuntimeMXBean();

    public double getTotal() {
        return mb(total);
    }

    public double getMax() {
        return mb(max);
    }

    public double getFree() {
        return mb(free);
    }

    public double getUsed() {
        return mb(total - free);
    }

    /** JVM 使用率（%） */
    public double getUsage() {
        return total <= 0 ? 0 : Math.round((total - free) / total * 10000) / 100.0;
    }

    /** Java 名称（如 Java HotSpot(TM) 64-Bit Server VM） */
    public String getName() {
        return RUNTIME.getVmName();
    }

    /** 启动时间 */
    public String getStartTime() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(RUNTIME.getStartTime()));
    }

    /** 运行时长（x天x小时x分钟x秒） */
    public String getRunTime() {
        Duration d = Duration.ofMillis(RUNTIME.getUptime());
        long days = d.toDays();
        long hours = d.toHours() % 24;
        long minutes = d.toMinutes() % 60;
        long seconds = d.getSeconds() % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天");
        if (hours > 0) sb.append(hours).append("小时");
        if (minutes > 0) sb.append(minutes).append("分钟");
        sb.append(seconds).append("秒");
        return sb.toString();
    }

    /** 启动参数 */
    public String getInputArgs() {
        return RUNTIME.getInputArguments().toString();
    }

    private static double mb(double v) {
        return Math.round(v / 1048576.0 * 100) / 100.0;
    }
}
