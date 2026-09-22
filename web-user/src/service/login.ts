import service from './request';

const TOKEN_KEY = 'music_token';

// 注册（用户名 / 密码 / 可选昵称）
export function register(username: string, password: string, nickname?: string) {
  return service.post('/auth/register', { username, password, nickname });
}

// 用户名 + 密码登录
export function login(username: string, password: string) {
  return service.post('/login', { username, password });
}

// 退出登录
export function logout() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem('isLogin');
  localStorage.removeItem('userProfile');
  return service.get('/logout?timestamp=' + new Date().getTime());
}

// 获取登录状态
export function getLoginStatus() {
  return service.get('/login/status?timestamp=' + Date.now());
}
