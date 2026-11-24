import { fileURLToPath } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Cùng quy tắc với backend: APP_SEED_PASSWORD trống thì dùng APP_ADMIN_PASSWORD
function demoPasswords(mode: string) {
  const env = loadEnv(mode, fileURLToPath(new URL('..', import.meta.url)), 'APP_')
  const admin = env.APP_ADMIN_PASSWORD ?? ''
  return { admin, seed: env.APP_SEED_PASSWORD?.trim() ? env.APP_SEED_PASSWORD : admin }
}

// https://vite.dev/config/
export default defineConfig(({ command, mode }) => ({
  // Chỉ dev server mới nhận mật khẩu demo từ .env ở gốc dự án; bản build luôn là null
  define: {
    __DEMO_PASSWORDS__: JSON.stringify(command === 'serve' ? demoPasswords(mode) : null),
  },
  // Dùng chung .env ở gốc dự án; trình duyệt chỉ thấy các biến VITE_*
  envDir: '..',
  plugins: [
    react(),
    tailwindcss(),
  ],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}))
