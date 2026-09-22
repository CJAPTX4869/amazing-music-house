package com.example.music.service;

import com.example.music.vo.FollowRequestVO;
import com.example.music.vo.UserVO;

import java.util.List;

/**
 * 好友关系服务（好友请求制）
 * <p>
 * 关系状态：none（无）/ pending_by_me（我发出的请求待对方处理）/
 * pending_to_me（对方发来的请求待我处理）/ friends（双方已互为好友）。
 */
public interface FollowService {

    /**
     * 发出好友请求
     * <p>
     * 幂等语义：已是好友或请求已在途时返回当前状态；
     * 若对方此前已向我发过请求，则直接接受合并为好友。
     *
     * @return 操作后的关系状态（pending_by_me / friends）
     */
    String requestFollow(Long targetId, Long userId);

    /**
     * 同意好友请求（status → 已互关）
     */
    void acceptFollow(Long requesterId, Long userId);

    /**
     * 移除关系：撤回我发出的请求 / 拒绝对方的请求 / 解除好友（按当前状态语义）
     */
    void removeFollow(Long targetId, Long userId);

    /**
     * 我与目标用户的关系状态
     *
     * @return none / pending_by_me / pending_to_me / friends
     */
    String getFollowStatus(Long targetId, Long userId);

    /**
     * 我收到的待处理好友请求（页头通知数据源）
     */
    List<FollowRequestVO> getPendingRequests(Long userId);
    /**
     * 我的好友列表（双向互关，status=已互关）
     *
     * @param userId 当前登录用户
     * @return 好友用户信息（含头像/昵称/签名/地区）
     */
    List<UserVO> getFriends(Long userId);
}
