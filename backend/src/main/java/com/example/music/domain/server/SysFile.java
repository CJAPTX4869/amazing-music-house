package com.example.music.domain.server;

import lombok.Data;

/**
 * 磁盘状态（参考若依/zzyl 监控模块）
 */
@Data
public class SysFile {

    /** 盘符路径 */
    private String dirName;
    /** 文件系统（如 NTFS） */
    private String sysTypeName;
    /** 盘符类型（本地磁盘/可移动磁盘…） */
    private String typeName;
    /** 总大小（格式化字符串） */
    private String total;
    /** 空闲大小 */
    private String free;
    /** 已用大小 */
    private String used;
    /** 已用百分比 */
    private double usage;
}
