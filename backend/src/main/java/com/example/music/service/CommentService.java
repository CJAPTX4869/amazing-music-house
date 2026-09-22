package com.example.music.service;

import com.example.music.dto.CommentCreateDTO;
import com.example.music.dto.ReplyCreateDTO;
import com.example.music.vo.CommentVO;
import com.example.music.vo.PageVO;
import org.springframework.core.io.Resource;

/**
 * 歌曲评论服务
 * <p>
 * 嵌套规则：所有回复的 parent_id 统一指向所属根评论（显示平铺两层，
 * reply_user_id 标记"回复@谁"），任何人（含根评论主）可无限追加追评。
 */
public interface CommentService {

    /**
     * 歌曲评论分页（根评论分页，楼中楼回复随根带出）
     *
     * @param songId  歌曲 ID
     * @param page    页码（从 1 起）
     * @param size    每页条数（内部收敛 1–50）
     * @param sort    hot-按点赞排序 / new-按时间倒序（默认）
     * @param viewerId 当前登录用户（匿名浏览时为 null，用于填充 liked 标记）
     */
    PageVO<CommentVO> page(Long songId, int page, int size, String sort, Long viewerId);

    /**
     * 发表根评论（可带配图；同时维护歌曲 comment_count）
     */
    CommentVO create(Long songId, CommentCreateDTO dto, Long userId);

    /**
     * 楼中楼回复/追评（parent_id 自动挂到所属根评论）
     */
    CommentVO reply(Long commentId, ReplyCreateDTO dto, Long userId);

    /**
     * 删除评论（本人或管理员；删根评论=整楼隐藏，comment_count 相应扣减）
     */
    void delete(Long id, Long userId);

    /**
     * 点赞评论（重复点赞幂等；like_count 冗余 +1）
     */
    void like(Long id, Long userId);

    /**
     * 取消点赞（未点赞时静默返回）
     */
    void unlike(Long id, Long userId);

    /**
     * 读取评论配图资源（评论不存在、无配图或文件缺失返回 null）
     */
    Resource getImageResource(Long id);
}
