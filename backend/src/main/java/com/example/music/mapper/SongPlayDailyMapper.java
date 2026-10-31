package com.example.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.music.entity.SongPlayDaily;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 歌曲每日播放统计 Mapper
 */
@Mapper
public interface SongPlayDailyMapper extends BaseMapper<SongPlayDaily> {

    /** 播放一次即累加当日计数（无行则插入，实时累加无需定时任务） */
    @Insert("INSERT INTO song_play_daily (song_id, stat_date, play_count) " +
            "VALUES (#{songId}, CURDATE(), 1) " +
            "ON DUPLICATE KEY UPDATE play_count = play_count + 1")
    int recordDailyPlay(@Param("songId") Long songId);

    /** 某首歌最近 N 天的每日播放量（运维曲线用） */
    @Select("SELECT stat_date AS date, play_count AS count FROM song_play_daily " +
            "WHERE song_id = #{songId} AND stat_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "ORDER BY stat_date")
    List<Map<String, Object>> selectDailyBySong(@Param("songId") Long songId, @Param("days") int days);

    /** 全部歌曲的每日播放行（数据透视用；只含有播放记录的行，零值由前端补齐） */
    @Select("SELECT song_id, stat_date, play_count FROM song_play_daily " +
            "WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) ORDER BY stat_date")
    List<Map<String, Object>> selectAllDaily(@Param("days") int days);

    /** 级联删除歌曲时物理清除每日统计 */
    @Delete("DELETE FROM song_play_daily WHERE song_id = #{songId}")
    int deleteBySongId(@Param("songId") Long songId);
}
