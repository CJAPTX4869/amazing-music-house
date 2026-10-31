package com.example.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.music.entity.Playlist;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 歌单 Mapper
 */
public interface PlaylistMapper extends BaseMapper<Playlist> {

    /** 歌单播放一次（播放全部按钮，推荐歌单"高播放"排序用） */
    @Update("UPDATE playlist SET play_count = play_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementPlayCount(@Param("id") Long id);
}
