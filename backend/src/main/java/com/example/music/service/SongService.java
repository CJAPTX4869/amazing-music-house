package com.example.music.service;

import com.example.music.dto.SongQueryDTO;
import com.example.music.dto.SongUpdateDTO;
import com.example.music.dto.SongUploadDTO;
import com.example.music.entity.Song;
import com.example.music.vo.PageVO;
import com.example.music.vo.SongVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 歌曲服务
 */
public interface SongService {

    /**
     * 上传歌曲（仅管理员）：校验 → 秒传判断 → 落盘 → ID3 解析 → 入库
     */
    SongVO upload(SongUploadDTO dto, Long uploaderId);

    /**
     * 分页查询
     *
     * @param adminView true=管理端（含下架歌曲，可按状态筛选）；false=公开端（仅上架）
     */
    PageVO<SongVO> page(SongQueryDTO query, boolean adminView);

    /**
     * 实体批量转 VO（含上传者昵称填充）；供运维"最火歌曲"等自定义查询复用
     */
    List<SongVO> convertToVO(List<Song> songs);

    /**
     * 歌曲详情（含下架歌曲）
     */
    SongVO detail(Long id);

    /**
     * 编辑歌曲信息（仅管理员）
     */
    SongVO update(Long id, SongUpdateDTO dto);

    /**
     * 删除单曲（逻辑删除 + 物理文件清理）
     */
    void delete(Long id);

    /**
     * 批量删除
     */
    void batchDelete(List<Long> ids);

    /**
     * 上架 / 下架
     */
    void updateStatus(Long id, Integer status);

    /**
     * MD5 预检（秒传）：命中返回已有歌曲，未命中返回 null
     */
    SongVO checkMd5(String md5);

    /**
     * 获取可下载/播放的歌曲（不存在 → 404，已下架 → 403）
     */
    Song getPlayableSong(Long id);

    /**
     * 异步记录下载行为（计数 +1、写入记录表）
     */
    void recordDownloadAsync(Long songId, Long userId, String ip, String userAgent);

    /**
     * 异步记录播放行为（计数 +1）
     */
    void recordPlayAsync(Long songId);

    /**
     * 上传 / 替换歌词（仅管理员）
     * <p>
     * 顺序：落盘新歌词 → 更新 lyric_path → 删除旧歌词文件（失败不丢失原歌词）。
     *
     * @param id   歌曲 ID
     * @param file 歌词文件（.lrc / .txt，≤ 1MB）
     */
    SongVO uploadLyric(Long id, MultipartFile file);

    /**
     * 读取歌词文本（无歌词或文件缺失时返回空串，便于前端直接渲染空态）
     *
     * @param id 歌曲 ID
     */
    String getLyricContent(Long id);

    /**
     * 上传 / 替换封面图片（仅管理员）
     * <p>
     * 顺序：落盘新封面 → 更新 cover_path → 删除旧封面文件（失败不丢失原封面）。
     * 图片由前端压缩裁剪为正方形后上传，服务端只做格式与体积校验。
     *
     * @param id   歌曲 ID
     * @param file 图片文件（jpg / jpeg / png / webp / gif，≤ 5MB）
     */
    SongVO uploadCover(Long id, MultipartFile file);

    /**
     * 加载封面图片资源（无封面时返回 null，由控制层返回 404）
     *
     * @param id 歌曲 ID
     */
    Resource getCoverResource(Long id);
}
