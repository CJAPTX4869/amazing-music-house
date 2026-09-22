package com.example.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.music.entity.PlaylistSong;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 歌单-歌曲关联 Mapper
 */
public interface PlaylistSongMapper extends BaseMapper<PlaylistSong> {

    /**
     * 查询歌单内的歌曲 ID（按加入顺序，即自增 id 升序）
     *
     * @param playlistId 歌单 ID
     * @return 歌曲 ID 列表（无数据时返回空列表）
     */
    @Select("SELECT song_id FROM playlist_song WHERE playlist_id = #{playlistId} ORDER BY sort_order ASC, id ASC")
    List<Long> selectSongIdsByPlaylistId(@Param("playlistId") Long playlistId);
}
