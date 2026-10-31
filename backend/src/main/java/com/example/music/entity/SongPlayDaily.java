package com.example.music.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 歌曲每日播放统计（播放接口实时累加：一天一行，ON DUPLICATE KEY UPDATE）
 */
@Data
@TableName("song_play_daily")
public class SongPlayDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 歌曲 ID */
    private Long songId;

    /** 统计日期 */
    private LocalDate statDate;

    /** 当日播放次数 */
    private Integer playCount;
}
