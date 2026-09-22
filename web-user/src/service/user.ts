import service from './request';

// 获取账号信息
export function getUserInfo() {
  return service.get('/user/account');
}

//获取用户详情
export function getUserDetail(uid:string) {
  return service.get('/user/detail?uid='+uid);
}

//更新用户信息（当前仅有数据源的字段：昵称 / 个性签名）
export function updateUserInfo(data: {
  nickname?: string,
  signature?: string,
}) {
  return service.put('/users/profile', data);
}

// 更新头像（需已登录；上传成功后返回新的头像地址）
export function updateUserAvatar(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return service.post('/users/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  });
}

//获取用户歌单
export function getUserPlaylist(uid:number) {
  return service.get('/user/playlist?uid='+uid);
}

// 用户公开主页资料（V3.1：含好友数量统计）
export function getUserPublicProfile(id: number | string) {
  return service.get(`/users/${id}/profile`);
}

// 用户主页歌单集合 {created, favorite, collected}
export function getUserPlaylists(id: number | string) {
  return service.get(`/users/${id}/playlists`);
}

// 收藏他人公开歌单
export function collectPlaylist(id: number | string) {
  return service.post(`/playlists/${id}/collect`);
}

// 取消收藏歌单
export function uncollectPlaylist(id: number | string) {
  return service.delete(`/playlists/${id}/collect`);
}

// 查询当前用户是否已收藏该歌单
export function checkPlaylistCollected(id: number | string) {
  return service.get(`/playlists/${id}/collect/check`);
}
