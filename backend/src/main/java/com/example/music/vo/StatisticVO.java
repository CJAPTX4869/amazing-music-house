package com.example.music.vo;

import lombok.Data;

/**
 * 管理端数据统计响应对象
 */
@Data
public class StatisticVO {

    /** 歌曲总数（不含已删除） */
    private Long songCount;

    /** 上架歌曲数 */
    private Long onlineSongCount;

    /** 用户总数 */
    private Long userCount;

    /** 累计下载量 */
    private Long totalDownload;

    /** 累计播放量 */
    private Long totalPlay;

    /** 磁盘已用（字节） */
    private Long diskUsed;

    /** 磁盘总量（字节） */
    private Long diskTotal;

    /** 磁盘使用描述，如 12.34 GB / 100.00 GB */
    private String diskUsedText;
}
