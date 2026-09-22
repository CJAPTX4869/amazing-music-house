package com.example.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 下载记录实体（对应表 download_record）
 * <p>
 * 日志表：仅记录，不做逻辑删除，没有 update_time。
 */
@Data
@TableName("download_record")
public class DownloadRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long songId;

    /** 下载用户 ID（null = 游客） */
    private Long userId;

    private String ip;

    private String userAgent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
