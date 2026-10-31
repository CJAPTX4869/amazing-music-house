import qs from 'qs';
import service from './request';
// 推荐歌曲
export function getRecommendSong() {
  // 无数据源，映射为新歌速递
  return service.get('/top/song?type=0');
}
// 新歌速递
/**
 * 最新音乐（V3.2 类型标签版）
 * - 全部：最新 100 首
 * - 华语/欧美/日本/韩国/纯音乐/其他：按歌曲标签精确筛选
 */
export function getTopSong(type: string = '全部') {
  if (type === '全部') {
    return service.get('/top/song?category=latest100&limit=100');
  }
  return service.get(`/top/song?genre=${encodeURIComponent(type)}&limit=100`);
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
