import qs from 'qs';
import service from './request';
// 推荐歌单
export function getPersonalized(limit = 15) {
  return service.get(`/personalized?limit=${limit}`);
}
// 获取歌单详情
export function getPlaylistDetail(id: string) {
  const query = qs.stringify({
    id,
    timestamp: Date.now()
  });
  return service.get('/playlist/detail?'+query);
}
// 获取歌单所有数据
export function getPlaylistAllDetail(data:{
  id: string,
  limit?: number,
  offset?: number,
}) {
  const query = qs.stringify({
    ...data,
    timestamp: Date.now(),
    limit:data.limit ? data.limit : 300,
    offset:data.offset ? data.offset : 0,
  });
  return service.get('/playlist/track/all?'+query);
}
// 编辑歌单（对接后端 PUT /playlists/{id}）
// 说明：后端目前仅支持 name / description 两个字段（tags 暂无存储位置，故不提交）
export function updatePlayListInfo(data:{
  id:string;
  name:string;
  tags?:string;
  desc?:string;
}) {
  return service.put(`/playlists/${data.id}`, {
    name: data.name,
    description: data.desc || ''
  });
}
// 上传 / 替换歌单封面（需登录且为歌单所有者；成功返回歌单详情，含 coverUrl 接口路径）
export function updatePlaylistCover(id: string | number, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return service.post(`/playlists/${id}/cover`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  });
}
//对歌单添加或删除歌曲
export function updatePlaylistTracks(data:{
  tracks:number;//歌曲id
  pid:number;// 歌单id
  op:'add'|'del';// 1:添加，2:删除
}) {
  const query = qs.stringify({
    tracks: data.tracks,
    pid: data.pid,
    op: data.op,
    timestamp: Date.now()
  });
  return service.get('/playlist/tracks?'+query);
}
// 新建歌单
export function createPlaylist(data:{
  name:string;
  privacy?:string;
}) {
  const query = qs.stringify({
    name: data.name,
    privacy: data.privacy,
    timestamp: Date.now()
  });
  return service.get('/playlist/create?'+query);
}
/**
 * 保存歌单自定义排序（拖拽后调用）
 * @param id 歌单 id
 * @param songIds 歌单内歌曲的完整排列（按目标顺序）
 */
export function reorderPlaylistSongs(id: number | string, songIds: number[]) {
  return service.put(`/playlists/${id}/songs/order`, { songIds });
}

/**
 * 从歌单中【真移除】一首歌（删除 playlist_song 关联行，不是伪移除）
 * 仅歌单属主可操作
 */
export function removePlaylistSong(playlistId: number | string, songId: number) {
  return service.delete(`/playlists/${playlistId}/songs/${songId}`);
}

/**
 * 取消收藏（用于「我喜欢的音乐」：同样是真移除，并同步维护 like_count）
 */
export function unfavoriteSong(songId: number) {
  return service.delete(`/playlists/favorite/${songId}`);
}