package com.example.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.music.entity.Song;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 歌曲 Mapper
 */
@Mapper
public interface SongMapper extends BaseMapper<Song> {

    /**
     * 下载计数 +1（原子更新，避免"读-改-写"并发丢失）
     */
    @Update("UPDATE song SET download_count = download_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementDownloadCount(@Param("id") Long id);

    /**
     * 播放计数 +1（原子更新）
     */
    @Update("UPDATE song SET play_count = play_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementPlayCount(@Param("id") Long id);

    /**
     * 累计下载量（管理端统计用）
     */
    @Select("SELECT IFNULL(SUM(download_count), 0) FROM song WHERE deleted = 0")
    Long sumDownloadCount();

    /**
     * 累计播放量（管理端统计用）
     */
    @Select("SELECT IFNULL(SUM(play_count), 0) FROM song WHERE deleted = 0")
    Long sumPlayCount();

    /** 物理删除歌曲行（级联删除用；绕过 @TableLogic 逻辑删除） */
    @Delete("DELETE FROM song WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    /** 最火歌曲·播放量 TOP N（同播放量看评论数，再同看先创建的） */
    @Select("SELECT * FROM song WHERE deleted = 0 AND status = 1 " +
            "ORDER BY play_count DESC, comment_count DESC, id ASC LIMIT #{limit}")
    List<Song> selectTopByPlays(@Param("limit") int limit);

    /** 最火歌曲·评论数 TOP N（同评论数看播放量，再同看先创建的） */
    @Select("SELECT * FROM song WHERE deleted = 0 AND status = 1 " +
            "ORDER BY comment_count DESC, play_count DESC, id ASC LIMIT #{limit}")
    List<Song> selectTopByComments(@Param("limit") int limit);
}
