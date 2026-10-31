package com.example.music.domain.server;

import lombok.Data;

/**
 * 服务器（操作系统）信息（参考若依/zzyl 监控模块）
 */
@Data
public class Sys {

    /** 服务器名称 */
    private String computerName;
    /** 服务器 IP */
    private String computerIp;
    /** 项目路径 */
    private String userDir;
    /** 操作系统 */
    private String osName;
    /** 系统架构 */
    private String osArch;
    /** 系统版本号 */
    private String osVersion;
    /** 系统启动时间 */
    private String bootTime;
    /** 系统已运行时长 */
    private String osUptime;
    /** 系统进程数 */
    private int processCount;
    /** 系统线程数 */
    private int threadCount;
}
