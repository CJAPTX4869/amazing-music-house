import service from './request';

/**
 * 好友关系接口（好友请求制）
 * 关系状态：none / pending_by_me / pending_to_me / friends
 */
export function requestFollow(id: number | string) {
  return service.post(`/users/${id}/follow`);
}

export function acceptFollow(id: number | string) {
  return service.post(`/users/${id}/follow/accept`);
}

export function removeFollow(id: number | string) {
  return service.delete(`/users/${id}/follow`);
}

export function getFollowStatus(id: number | string) {
  return service.get(`/users/${id}/follow/status`);
}

/** 我收到的待处理好友请求（页头通知数据源） */
export function getPendingRequests() {
  return service.get('/users/follow/requests');
}
/** 我的好友列表（已互相关注的好友） */
export function getFriends() {
  return service.get('/users/friends');
}