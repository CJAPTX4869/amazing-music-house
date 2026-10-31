package com.example.music.domain.server;

import lombok.Data;

/**
 * CPU 信息（参考若依/zzyl 监控模块）
 * <p>
 * 字段保存的是 OSHI 的原始 tick 数，getter 统一换算为百分比。
 */
@Data
public class Cpu {

    /** 逻辑核心数 */
    private int cpuNum;
    /** 物理核心数 */
    private int physicalNum;
    /** CPU 型号 */
    private String cpuName;
    /** 本应用进程 CPU 占用（%） */
    private double processLoad;
    /** CPU 总 tick */
    private double total;
    /** 系统 tick */
    private double sys;
    /** 用户 tick */
    private double used;
    /** 等待 tick */
    private double wait;
    /** 空闲 tick */
    private double free;

    public double getTotal() {
        return Math.round(total * 100) / 100.0;
    }

    public double getSys() {
        return round(total <= 0 ? 0 : sys / total * 100);
    }

    public double getUsed() {
        return round(total <= 0 ? 0 : used / total * 100);
    }

    public double getWait() {
        return round(total <= 0 ? 0 : wait / total * 100);
    }

    public double getFree() {
        return round(total <= 0 ? 0 : free / total * 100);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
