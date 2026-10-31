import axios from 'axios';
import obverser from '@/utils/obverser';
import { getApiBase } from '@/config';

const TOKEN_KEY = 'music_token';

// create axios instance
const instance = axios.create({
  // baseURL 不写死：由请求拦截器按运行时配置注入（见 src/config.ts，改 config.json 即可换后端）
  method: 'get',
  // 全局默认超时：避免后端无响应时请求永久挂起（上传等长耗时接口自行覆盖更长超时）
  timeout: 15000,
  withCredentials: false,
});

/**
 * 资源地址归一化
 * <p>
 * 后端返回的封面/头像/表情都是"根相对路径"（/emojis/1/image、/users/11/avatar、
 * /songs/219/cover…），前端直接当 src 用会打到前端自己的源（桌面客户端 file:// 更直接 404），
 * 表现为表情与头像全部破图。这里在响应层统一补上运行时配置里的后端地址。
 */
const ASSET_KEYS = ['avatarUrl', 'imageUrl', 'coverImgUrl', 'picUrl', 'cover', 'backgroundUrl', 'streamUrl', 'downloadUrl'];
const API_PATH_RE = /^\/(api\/)?(emojis|songs|users|comments|playlists|files)\//i;

function toAbsoluteUrl(value: string): string {
  const base = getApiBase();                                  // http://host:8080/api
  const origin = base.replace(/\/api\/?$/i, '');
  return /^\/api\//i.test(value)
    ? origin + value
    : base + value;
}

function absolutizeAssets(node: any, depth = 0): any {
  if (depth > 8 || node === null || typeof node !== 'object') return node;
  if (Array.isArray(node)) {
    for (let i = 0; i < node.length; i += 1) node[i] = absolutizeAssets(node[i], depth + 1);
    return node;
  }
  Object.keys(node).forEach((key) => {
    const value = node[key];
    if (typeof value === 'string' && /^\//.test(value)
      && (ASSET_KEYS.includes(key) || API_PATH_RE.test(value))) {
      node[key] = toAbsoluteUrl(value);
    } else if (value && typeof value === 'object') {
      node[key] = absolutizeAssets(value, depth + 1);
    }
  });
  return node;
}
// request interceptor: inject JWT
// 客户端平台标识：Electron 桌面端 UA 自带 Electron，无需特殊处理
function clientPlatform(): string {
  const ua = navigator.userAgent.toLowerCase();
  if (ua.includes('electron')) return 'desktop';
  if (/android|iphone|ipad|mobile/.test(ua)) return 'mobile';
  return 'web';
}

instance.interceptors.request.use((config) => {
  config.baseURL = getApiBase();
  config.headers['X-Client-Platform'] = clientPlatform();
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, err => {
  return Promise.reject(err);
});

/**
 * 登录态失效统一处理
 * <p>
 * 注意：后端约定「业务错误同样返回 HTTP 200」，令牌失效的错误码在响应体 code 字段（401），
 * 所以必须在**成功分支**判断，否则 token 过期后本地登录态永远不会被清理。
 */
const handleAuthExpired = (message?: string) => {
  // 本来就没登录时（匿名撞上后端 @RequireLogin）不能走这套：
  // 否则会对从没登录过的人弹「登录已过期」，还会广播 authExpired 把界面打回未登录态
  if (!localStorage.getItem(TOKEN_KEY)) return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem('isLogin');
  localStorage.removeItem('userProfile');
  window.$message?.error(message || '登录已过期，请重新登录');
  // 通知组件重置内存中的登录态（obverser 无订阅者时会抛错，需兜底）
  try {
    obverser.emit('authExpired');
  } catch (e) {
    // 暂无监听者，忽略
  }
};

//response interceptor
instance.interceptors.response.use((response) => {
  if (response && response.data) absolutizeAssets(response.data);
  const body = response.data;
  if (body && typeof body === 'object' && body.code === 401) {
    handleAuthExpired(body.message);
  }
  return response;
}, err => {
  // 非 2xx：真实网络异常或网关错误
  if (err.response?.status === 401) {
    handleAuthExpired();
  } else {
    window.$message?.error(err.response?.data?.message || '网络异常，请稍后重试');
  }
  return Promise.reject(err);
});

export default instance;

// 在线心跳：每 30 秒上报一次活跃状态（后端 90 秒 TTL 判离线；未登录静默跳过）
setInterval(() => {
  if (localStorage.getItem(TOKEN_KEY)) {
    instance.get('/auth/profile').catch(() => {});
  }
}, 30000);
