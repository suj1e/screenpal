const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 8080;
const ROOT = path.resolve(__dirname);

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css',
  '.js': 'application/javascript',
  '.json': 'application/json',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

const server = http.createServer((req, res) => {
  let filePath = path.join(ROOT, req.url === '/' ? '/01_home.html' : req.url);
  const ext = path.extname(filePath);
  const contentType = MIME[ext] || 'application/octet-stream';

  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('404 - 文件不存在');
      return;
    }
    res.writeHead(200, { 'Content-Type': contentType });
    res.end(data);
  });
});

server.listen(PORT, () => {
  console.log(`ScreenPal 原型服务已启动`);
  console.log(`  本地访问: http://localhost:${PORT}/`);
  console.log(`  页面列表:`);
  console.log(`    http://localhost:${PORT}/01_home.html          - 主界面（配置页）`);
  console.log(`    http://localhost:${PORT}/02_floating_window.html - 悬浮球效果`);
  console.log(`    http://localhost:${PORT}/03_selection_overlay.html - 框选界面`);
  console.log(`    http://localhost:${PORT}/04_ocr_result.html      - 识别结果 + 播报`);
  console.log(`\n  按 Ctrl+C 停止服务`);
});
