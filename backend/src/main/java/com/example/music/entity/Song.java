package com.example.music.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌曲实体（对应表 song）
 */
@Data
@TableName("song")
public class Song {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 歌曲名 */
    private String title;

    private String artist;

    private String album;

    /** 标签（逗号分隔，最多 3 个；V3.2 新增，替代原 genre 风格列） */
    private String tags;

    /** 时长（秒） */
    private Integer duration;

    /** 上传时的原始文件名（仅用于下载回显） */
    private String originalName;

    /** 落盘文件名（UUID + 扩展名） */
    private String fileName;

    /** 相对存储路径，如 audio/2026/09/15/xxx.mp3 */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件 MD5（去重/秒传） */
    private String fileMd5;

    private String mimeType;

    /** 扩展名（小写，不含点） */
    private String ext;

    /** 封面相对路径 */
    private String coverPath;

    /** 歌词文件相对路径（LRC，可空） */
    private String lyricPath;

    /** 网易云歌曲 ID（管理端导入歌词/封面的来源锚点，幂等唯一） */
    private Long neteaseId;

    /** 上传者 ID（NOT NULL） */
    private Long uploaderId;

    private Integer downloadCount;

    private Integer playCount;

    /** 喜欢数（冗余：用户收藏进"我喜欢的音乐"时维护） */
    private Integer likeCount;

    /** 评论数（冗余：含楼中楼回复，评论增删时维护） */
    private Integer commentCount;

    /** 状态：1-正常（上架） 0-下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
