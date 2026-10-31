package com.example.music.domain.server;

import lombok.Data;

/**
 * 物理内存信息（参考若依/zzyl 监控模块）
 */
@Data
public class Mem {

    /** 总量（字节） */
    private long total;
    /** 已用（字节） */
    private long used;
    /** 空闲（字节） */
    private long free;
    /** 虚拟内存总量（字节，含页面文件） */
    private long swapTotal;
    /** 虚拟内存已用（字节） */
    private long swapUsed;

    public double getTotal() {
        return div(total);
    }

    public double getUsed() {
        return div(used);
    }

    public double getFree() {
        return div(free);
    }

    /** 内存使用率（%） */
    public double getUsage() {
        return total <= 0 ? 0 : Math.round((double) used / total * 10000) / 100.0;
    }

    public double getSwapTotal() {
        return div(swapTotal);
    }

    public double getSwapUsed() {
        return div(swapUsed);
    }

    private static double div(long v) {
        return Math.round(v / 1073741824.0 * 100) / 100.0;
    }
}
