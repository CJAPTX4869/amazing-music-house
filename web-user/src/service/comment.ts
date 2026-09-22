import service from './request';

/**
 * 歌曲评论相关接口
 * <p>
 * 浏览类（列表/表情）匿名可访问；写操作（发评/回复/点赞/删除/传图）需登录，
 * 未登录时后端返回 code=401，由 request.ts 拦截器统一登出处理。
 */

/** 歌曲评论分页（sort: hot-按点赞 / new-按时间） */
export function getSongComments(songId: string | number, page = 1, size = 20, sort = 'new') {
  return service.get(`/songs/${songId}/comments`, {
    params: { page, size, sort, timestamp: Date.now() }
  });
}

/** 发表根评论（imagePath 为先调 uploadCommentImage 得到的相对路径） */
export function sendComment(songId: string | number, data: { content: string; imagePath?: string }) {
  return service.post(`/songs/${songId}/comments`, data);
}

/** 楼中楼回复/追评（replyUserId 不传默认回复被回复评论作者） */
export function replyComment(commentId: string | number, data: { content: string; replyUserId?: number }) {
  return service.post(`/comments/${commentId}/replies`, data);
}

/** 删除评论（本人或管理员；删根评论=整楼隐藏） */
export function deleteComment(commentId: string | number) {
  return service.delete(`/comments/${commentId}`);
}

/** 点赞评论（重复点赞幂等） */
export function likeComment(commentId: string | number) {
  return service.post(`/comments/${commentId}/like`);
}

/** 取消点赞 */
export function unlikeComment(commentId: string | number) {
  return service.delete(`/comments/${commentId}/like`);
}

/** 上传评论配图（含 GIF 动图；返回相对路径，发评论时作为 imagePath 提交） */
export function uploadCommentImage(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return service.post('/comments/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  });
}

/** 全量表情清单（评论表情面板数据源；imageUrl 为相对 API 路径） */
export function getEmojis() {
  return service.get('/emojis');
}
