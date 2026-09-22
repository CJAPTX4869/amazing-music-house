import qs from 'qs';
import service from './request';
// 推荐歌曲
export function getRecommendSong() {
  // 无数据源，映射为新歌速递
  return service.get('/top/song?type=0');
}
// 新歌速递
export function getTopSong(type: 0 | 7 | 96 |8 | 16=0) {
  return service.get(`/top/song?type=${type}`);
}
// 我喜欢的音乐列表
export function getLikeList(uid: number) {
  const query = qs.stringify({
    uid,
  });
  return service.get(`/likelist?${query}`);
}
// 获取音乐url
export function getMusicUrl(id:string) {
  const query = qs.stringify({
    level:'standard',
    id
  });
  return service.get('/song/url/v1?'+query);
}
// 获取歌词
export function getLyric(id:string) {
  return service.get('/lyric/new?id='+id);
}
// 获取逐字歌词
export function getNewLyric(id:string) {
  return service.get('/lyric/new?id='+id);
}
// 检查音乐是否可用
export function checkMusic(id:string) {
  return service.get('/check/music?id='+id);
}
// 喜欢音乐
export function likeMusic(id:number, like:boolean) {
  return service.get(`/like?id=${id}&like=${like}`); 
}
