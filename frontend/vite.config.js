import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    // Monaco 0.57 also vendors DOMPurify; npm overrides alone do not replace that copy.
    resolve: { alias: [{ find: /^\.\/dompurify\/dompurify\.js$/,
      replacement: fileURLToPath(new URL('./node_modules/dompurify/dist/purify.es.mjs', import.meta.url)) }] },
    plugins: [vue()],
    server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy: {
      '/api': { target: env.API_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
    }},
    preview: { host: '127.0.0.1', port: 4173, strictPort: true, proxy: {
      '/api': { target: env.API_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: true },
    }},
  }
})
