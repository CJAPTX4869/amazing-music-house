package com.example.music.vo;

import com.example.music.entity.Song;
import com.example.music.utils.FormatUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 歌曲响应对象
 * <p>
 * downloadUrl / streamUrl 为「API 基础路径下的相对路径」（不含 /api 前缀），
 * 前端 axios baseURL 已配置为 /api，可直接拼接使用。
 */
@Data
public class SongVO {

    private Long id;
    private String title;
    private String artist;
    private String album;
    /** 标签（逗号分隔，最多 3 个；V3.2 新增，替代原 genre） */
    private String tags;

    /** 时长（秒） */
    private Integer duration;

    /** 时长文本，如 04:29 */
    private String durationText;

    private String fileName;
    private String originalName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件大小文本，如 5.00 MB */
    private String fileSizeText;

    private String fileMd5;
    private String mimeType;
    private String ext;
    private String coverUrl;

    /** 是否已有歌词（前端据此决定是否请求歌词内容） */
    private Boolean hasLyric;

    private Long uploaderId;

    /** 上传者昵称（由 Service 填充） */
    private String uploaderName;

    private Integer downloadCount;
    private Integer playCount;

    /** 喜欢数（冗余计数，列表角标展示用，V3.1 新增） */
    private Integer likeCount;

    /** 评论数（冗余计数，含楼中楼回复，列表角标展示用，V3.1 新增） */
    private Integer commentCount;

    /** 1-正常 0-下架 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 下载地址（相对 API 基础路径） */
    private String downloadUrl;

    /** 播放地址（相对 API 基础路径） */
    private String streamUrl;

    /**
     * 实体 → VO（uploaderName 需由 Service 另行填充）
     */
    public static SongVO of(Song song) {
        if (song == null) {
            return null;
        }
        SongVO vo = new SongVO();
        vo.setId(song.getId());
        vo.setTitle(song.getTitle());
        vo.setArtist(song.getArtist());
        vo.setAlbum(song.getAlbum());
        vo.setTags(song.getTags());
        vo.setDuration(song.getDuration());
        vo.setDurationText(FormatUtils.duration(song.getDuration()));
        vo.setFileName(song.getFileName());
        vo.setOriginalName(song.getOriginalName());
        vo.setFileSize(song.getFileSize());
        vo.setFileSizeText(FormatUtils.fileSize(song.getFileSize()));
        vo.setFileMd5(song.getFileMd5());
        vo.setMimeType(song.getMimeType());
        vo.setExt(song.getExt());
        // 封面地址同样为「API 基础路径下的相对路径」；无封面时为 null（前端据此走占位图）
        vo.setCoverUrl(StringUtils.hasText(song.getCoverPath())
                ? "/songs/" + song.getId() + "/cover"
                : null);
        vo.setHasLyric(StringUtils.hasText(song.getLyricPath()));
        vo.setUploaderId(song.getUploaderId());
        vo.setDownloadCount(song.getDownloadCount());
        vo.setPlayCount(song.getPlayCount());
        vo.setLikeCount(song.getLikeCount() == null ? 0 : song.getLikeCount());
        vo.setCommentCount(song.getCommentCount() == null ? 0 : song.getCommentCount());
        vo.setStatus(song.getStatus());
        vo.setCreateTime(song.getCreateTime());
        vo.setUpdateTime(song.getUpdateTime());
        vo.setDownloadUrl("/songs/" + song.getId() + "/download");
        vo.setStreamUrl("/songs/" + song.getId() + "/stream");
        return vo;
    }
}
