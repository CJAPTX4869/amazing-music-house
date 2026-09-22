package com.example.music.service;

import com.example.music.dto.PlaylistSaveDTO;
import com.example.music.vo.PlaylistVO;
import com.example.music.vo.PageVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 歌单服务
 * <p>
 * 通用约定：所有方法均需登录；涉及歌单写操作时内部校验归属，越权返回 403。
 */
public interface PlaylistService {

    /**
     * 新建歌单
     */
    PlaylistVO create(PlaylistSaveDTO dto, Long userId);

    /**
     * 我的歌单列表（"我喜欢的音乐"置顶，其余按创建时间倒序）
     */
    List<PlaylistVO> myList(Long userId);

    /**
     * 歌单详情（含歌曲列表）
     */
    PlaylistVO detail(Long id, Long userId);

    /**
     * 歌单详情（**不做归属校验**）
     * <p>
     * 供旧前端兼容层等"公开读取"场景使用：旧前端的歌单详情页会展示非本人歌单。
     *
     * @param id 歌单 ID
     */
    PlaylistVO detailPublic(Long id);

    /**
     * 公开歌单列表（供推荐位使用）
     *
     * @param limit 条数上限（内部限制 1–50）
     */
    List<PlaylistVO> listPublic(int limit);

    /**
     * 编辑歌单（"我喜欢的音乐"不可重命名）
     */
    PlaylistVO update(Long id, PlaylistSaveDTO dto, Long userId);

    /**
     * 删除歌单（"我喜欢的音乐"不可删除；级联清理关联记录）
     */
    void delete(Long id, Long userId);

    /**
     * 向歌单添加歌曲（校验归属 + 歌曲上架状态）
     */
    void addSong(Long id, Long songId, Long userId);

    /**
     * 从歌单移除歌曲
     */
    void removeSong(Long id, Long songId, Long userId);

    /** 保存歌单自定义排序（仅歌单所有者可操作，需传当前歌单歌曲的完整排列） */
    void reorderSongs(Long id, List<Long> songIds, Long userId);

    /**
     * 收藏歌曲到"我喜欢的音乐"（歌单不存在时自动创建）
     */
    PlaylistVO favorite(Long songId, Long userId);

    /**
     * 取消收藏
     */
    void unfavorite(Long songId, Long userId);

    /**
     * 获取"我喜欢的音乐"（含歌曲列表，不存在时自动创建）
     */
    PlaylistVO getFavoriteList(Long userId);

    /**
     * 判断歌曲是否已被当前用户收藏
     */
    boolean isFavorited(Long songId, Long userId);

    /**
     * 上传 / 替换歌单封面（校验归属；「我喜欢的音乐」不支持自定义封面）
     */
    PlaylistVO uploadCover(Long id, MultipartFile file, Long userId);

    /**
     * 读取歌单封面资源（公开读取；无封面或文件缺失返回 {@code null}）
     */
    Resource getCoverResource(Long id);

    /* ==================== 用户主页与收藏歌单（V3.1） ==================== */

    /**
     * 用户主页歌单集合
     *
     * @param targetId 主页归属用户
     * @param viewerId 当前登录用户（本人可见私有歌单；匿名只看公开）
     * @return {created: 创建的歌单, favorite: 「我喜欢的音乐」歌单或 null, collected: 收藏的歌单}
     */
    Map<String, Object> userPlaylists(Long targetId, Long viewerId);

    /**
     * 公开歌单搜索（按歌单名模糊，兼容层搜索页"歌单"Tab 用）
     */
    PageVO<PlaylistVO> searchPublic(String keyword, int page, int size);

    /**
     * 收藏他人公开歌单（不能收藏自己的；重复收藏幂等；维护 collect_count）
     */
    void collect(Long playlistId, Long userId);

    /**
     * 取消收藏（未收藏时静默返回）
     */
    void uncollect(Long playlistId, Long userId);

    /**
     * 当前用户是否已收藏该歌单
     */
    boolean isCollected(Long playlistId, Long userId);
}
