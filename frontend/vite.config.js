import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root = path.dirname(fileURLToPath(import.meta.url));
const target =
  process.env.API_TARGET ||
  loadEnv(process.env.NODE_ENV || 'development', root, '').API_TARGET ||
  'http://127.0.0.1:8080';
// 登录 Cookie 与 CSRF 请求头原样转发，代理不读取后端凭据。
const proxy = Object.fromEntries(
  ['/api', '/health', '/doc.html', '/webjars', '/swagger-resources', '/v2/api-docs'].map(
    (prefix) => [prefix, { target, changeOrigin: true }],
  ),
);
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: true,
    fs: {
      strict: true,
      allow: [root],
      deny: ['**/.git/**', '**/.local/**', '**/.env*', '**/*.{crt,pem}'],
    },
    proxy,
  },
  preview: { port: 4173, strictPort: true, proxy },
});
