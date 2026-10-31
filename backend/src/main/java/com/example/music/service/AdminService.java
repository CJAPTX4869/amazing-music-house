package com.example.music.service;

import com.example.music.dto.UserQueryDTO;
import com.example.music.vo.PageVO;
import com.example.music.vo.StatisticVO;
import com.example.music.vo.UserVO;

/**
 * 管理端服务
 */
public interface AdminService {

    /**
     * 用户列表（分页 + 筛选）
     */
    PageVO<UserVO> pageUsers(UserQueryDTO query);

    /**
     * 启用 / 禁用用户（不可操作自身）
     */
    void updateUserStatus(Long userId, Integer status, Long currentAdminId);

    /**
     * 删除用户（连同其评论、点赞、播放/下载记录、歌单等数据）
     */
    void deleteUser(Long userId, Long currentAdminId);

    /**
     * 歌曲上架 / 下架
     */
    void updateSongStatus(Long songId, Integer status);

    /**
     * 数据统计
     */
    StatisticVO statistics();
}
