import service from './request';

const TOKEN_KEY = 'music_token';

// 注册（用户名 / 密码 / 可选昵称）
export function register(username: string, password: string, nickname?: string) {
  return service.post('/auth/register', { username, password, nickname });
}

// 用户名 + 密码登录
export function login(username: string, password: string, ticket?: string) {
  return service.post('/login', { username, password, ticket });
}

/** 抓娃娃验证：领取一次性票据（10 分钟有效） */
export function captchaChallenge() {
  return service.post('/auth/captcha/challenge');
}

/** 抓娃娃通过后：向服务端确认票据（登录时一次性消费） */
export function captchaVerify(ticket: string) {
  return service.post('/auth/captcha/verify', { ticket });
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
