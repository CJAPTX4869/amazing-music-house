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

    /**
     * 统计歌单内「仍然存在的歌曲」数量。
     * <p>
     * playlist_song 是物理删除的关联表，本身不记录歌曲是否已被删；
     * 直接数关联行会把指向已删歌曲的孤儿行也算进去，
     * 导致 song_count 与实际能列出的曲数对不上。这里 JOIN song 并过滤 deleted。
     */
    @Select("SELECT COUNT(*) FROM playlist_song ps JOIN song s ON s.id = ps.song_id "
            + "WHERE ps.playlist_id = #{playlistId} AND s.deleted = 0")
    int countLiveSongs(@Param("playlistId") Long playlistId);
}
