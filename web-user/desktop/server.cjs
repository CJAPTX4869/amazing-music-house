/**
 * 桌面客户端内置静态服务（无第三方依赖）
 * 作用：把已构建的 dist 目录用 http://127.0.0.1:5180 提供出来，
 * 这样 ① config.json 能被同源读取（file:// 下 fetch 会被浏览器拦掉）
 *      ② 应用运行在标准 http 源上，接口请求 / CORS 行为与网页版一致
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', 'dist');
const PORT = Number(process.env.MUSIC_APP_PORT || 5180);
const HOST = '127.0.0.1';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.mp3': 'audio/mpeg',
  '.flac': 'audio/flac',
  '.wav': 'audio/wav'
};

const server = http.createServer((req, res) => {
  let urlPath = decodeURIComponent((req.url || '/').split('?')[0]);
  if (urlPath === '/' || urlPath === '') urlPath = '/index.html';

  const filePath = path.join(ROOT, urlPath);
  // 目录穿越防护
  if (!filePath.startsWith(ROOT)) {
    res.writeHead(403).end('forbidden');
    return;
  }

  fs.readFile(filePath, (err, data) => {
    if (err) {
      // SPA 回退：未命中的路径一律返回 index.html（hash 路由需要）
      fs.readFile(path.join(ROOT, 'index.html'), (e2, html) => {
        if (e2) {
          res.writeHead(404).end('not found');
          return;
        }
        res.writeHead(200, {
          'Content-Type': MIME['.html'],
          'Cache-Control': 'no-store'
        });
        res.end(html);
      });
      return;
    }
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream',
      // index.html 与 config.json 一律不缓存：否则前端更新后浏览器仍加载旧页面
      // （Hashed 资源名走长缓存，靠文件名变化失效）
      'Cache-Control': (urlPath === '/config.json' || urlPath.endsWith('.html'))
        ? 'no-store'
        : 'public, max-age=3600'
    });
    res.end(data);
  });
});

server.listen(PORT, HOST, () => {
  console.log(`[music-desktop] serving ${ROOT} at http://${HOST}:${PORT}/`);
});
