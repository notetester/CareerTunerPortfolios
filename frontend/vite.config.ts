import { defineConfig } from 'vite'
import path from 'path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

const publicBase = process.env.VITE_PUBLIC_BASE ?? '/'

function figmaAssetResolver() {
  return {
    name: 'figma-asset-resolver',
    resolveId(id: string) {
      if (id.startsWith('figma:asset/')) {
        const filename = id.replace('figma:asset/', '')
        return path.resolve(__dirname, 'src/assets', filename)
      }
    },
  }
}

export default defineConfig({
  base: publicBase,
  plugins: [
    figmaAssetResolver(),
    // The React and Tailwind plugins are both required for Make, even if
    // Tailwind is not being actively used – do not remove them
    react(),
    tailwindcss(),
    // PWA(설치형 앱 셸) + 서비스워커. Capacitor 앱의 WebView 도 같은 빌드를 쓴다.
    // 캐시 정책은 docs/planning/모바일 고려.md §7.3 준수: 정적 자산만 precache 하고
    // /api 응답(프로필·분석·면접·결제 등 민감 데이터)은 절대 캐시하지 않는다(runtimeCaching 없음).
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['icons/apple-touch-icon.png', 'icons/favicon-32.png', 'icons/icon.svg'],
      manifest: {
        name: 'CareerTuner',
        short_name: 'CareerTuner',
        description: '채용공고에 맞춰 내 스펙과 면접 답변을 조정하는 AI 취업 전략 플랫폼',
        lang: 'ko',
        start_url: '.',
        scope: '.',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#ffffff',
        theme_color: '#030213',
        icons: [
          { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,woff,woff2}'],
        navigateFallback: 'index.html',
        // /api 는 SPA 폴백/캐시 대상에서 제외 — 백엔드로 직접 나가고 캐시하지 않는다.
        navigateFallbackDenylist: [/^\/api/],
        cleanupOutdatedCaches: true,
        maximumFileSizeToCacheInBytes: 3 * 1024 * 1024,
      },
    }),
  ],
  resolve: {
    alias: {
      // Alias @ to the src directory
      '@': path.resolve(__dirname, './src'),
    },
  },

  server: {
    port: 5173,
    proxy: {
      // Forward API calls to the Spring Boot backend during development.
      // Browser hits localhost:5173/api/*, Vite proxies to localhost:8080/api/*.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },

  build: {
    sourcemap: false,
  },

  // File types to support raw imports. Never add .css, .tsx, or .ts files to this.
  assetsInclude: ['**/*.svg', '**/*.csv'],
})
