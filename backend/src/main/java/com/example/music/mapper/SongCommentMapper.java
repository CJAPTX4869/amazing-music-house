package com.example.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.music.entity.SongComment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface SongCommentMapper extends BaseMapper<SongComment> {

    /** 级联删除歌曲时物理清除该歌全部评论（含已逻辑删除的，用户要求删除即彻底清除） */
    @Delete("DELETE FROM song_comment WHERE song_id = #{songId}")
    int physicalDeleteBySongId(@Param("songId") Long songId);

    /** 某首歌每天的评论条数（date=yyyy-MM-dd, count），单曲数据页双曲线用 */
    @Select("SELECT DATE(create_time) AS date, COUNT(*) AS count FROM song_comment " +
            "WHERE song_id = #{songId} AND create_time >= #{since} " +
            "GROUP BY DATE(create_time) ORDER BY date")
    List<Map<String, Object>> selectDailyBySong(@Param("songId") Long songId, @Param("since") LocalDate since);
}
