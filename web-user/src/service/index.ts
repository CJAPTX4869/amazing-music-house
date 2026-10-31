import service from './request';
// 获取轮播
export function getBanner() {
  return service.get('/banner');
}
export * from './playlist';
export * from './songs';
export * from './login';
export * from './user';
export * from './search';
export * from './comment';
export * from './follow';

// V3.3 播放量最高（发现页"推荐音乐"实时数据源）
export function getTopPlays(limit = 30) {
  return service.get(`/top/plays?limit=${limit}`);
}

// V3.3 评论数最高（发现页"推荐音乐"实时数据源）
export function getTopComments(limit = 20) {
  return service.get(`/top/comments?limit=${limit}`);
}
