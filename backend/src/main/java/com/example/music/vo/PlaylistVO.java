package com.example.music.vo;

import com.example.music.entity.Playlist;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 歌单响应对象
 */
@Data
public class PlaylistVO {

    private Long id;
    private String name;
    private String description;
    private String coverUrl;
    private Long userId;

    /** 创建者昵称（由 Service 填充） */
    private String userName;

    /** 0-普通歌单 1-我喜欢的音乐 */
    private Integer type;

    private Integer songCount;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /** 歌单内歌曲（仅详情与"我喜欢的音乐"填充） */
    private List<SongVO> songs;

    /**
     * 实体 → VO（userName 需由 Service 另行填充）
     */
    public static PlaylistVO of(Playlist playlist) {
        if (playlist == null) {
            return null;
        }
        PlaylistVO vo = new PlaylistVO();
        vo.setId(playlist.getId());
        vo.setName(playlist.getName());
        vo.setDescription(playlist.getDescription());
        // 封面统一输出接口路径（与 SongVO 风格一致），由 GET /playlists/{id}/cover 直出，
        // 不把存储相对路径暴露给前端；无封面输出 null（前端回退占位图）
        vo.setCoverUrl(StringUtils.hasText(playlist.getCoverPath())
                ? "/playlists/" + playlist.getId() + "/cover"
                : null);
        vo.setUserId(playlist.getUserId());
        vo.setType(playlist.getType());
        vo.setSongCount(playlist.getSongCount());
        vo.setStatus(playlist.getStatus());
        vo.setCreateTime(playlist.getCreateTime());
        vo.setUpdateTime(playlist.getUpdateTime());
        return vo;
    }
}
