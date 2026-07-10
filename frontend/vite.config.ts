import { defineConfig, loadEnv, type PluginOption, type UserConfig } from 'vite'
import path from 'path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import { sites } from './sites/vite-plugin'

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

export default defineConfig(async ({ mode }) => {
  // 환경 모드(.env.localhost/.env.tailscale/.env.aws/.env.domain)의 dev 프록시 대상.
  // 미지정 시 기존과 동일하게 로컬 백엔드(:8080). 상세: ../docs/ENVIRONMENTS.md
  const env = loadEnv(mode, __dirname, '')
  const proxyTarget = env.VITE_PROXY_TARGET || 'http://localhost:8080'
  const sitesPlugins: PluginOption[] = []

  if (mode === 'sites') {
    process.env.WRANGLER_WRITE_LOGS ??= 'false'
    process.env.WRANGLER_LOG_PATH ??= '.wrangler/logs'
    process.env.MINIFLARE_REGISTRY_PATH ??= '.wrangler/registry'

    const { cloudflare } = await import('@cloudflare/vite-plugin')
    sitesPlugins.push(
      sites(),
      cloudflare({
        config: {
          name: 'server',
          main: './worker/index.ts',
          compatibility_date: '2026-05-22',
          compatibility_flags: ['nodejs_compat'],
          assets: {
            binding: 'ASSETS',
            not_found_handling: 'single-page-application',
            run_worker_first: ['/api', '/api/*', '/__backup/*'],
          },
        },
      }),
    )
  }

  const config: UserConfig = {
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
        // /api 와 별도 정적 지식맵은 SPA 폴백/캐시 대상에서 제외한다.
        // Pages의 /Obsidian/* 요청을 index.html로 돌리면 React Router 404가 노출된다.
        navigateFallbackDenylist: [/^\/api/, /\/Obsidian(?:\/|$)/],
        cleanupOutdatedCaches: true,
        maximumFileSizeToCacheInBytes: 3 * 1024 * 1024,
        // Web Push 핸들러(push/notificationclick)를 생성 SW 에 합친다. public/push-sw.js 참고.
        importScripts: ['push-sw.js'],
      },
    }),
    ...sitesPlugins,
  ],
  resolve: {
    alias: {
      // Alias @ to the src directory
      '@': path.resolve(__dirname, './src'),
    },
  },

  server: {
    port: 5173,
    // Windows 기본 'localhost' 바인딩이 IPv6(::1)로 잡히면 tailscale serve(127.0.0.1)가 못 붙어 502 가 난다.
    // IPv4 루프백으로 고정해 serve 프록시가 항상 닿게 한다. (모바일 live reload 는 CLI --host 0.0.0.0 로 override)
    host: '127.0.0.1',
    // Tailscale serve(HTTPS)로 tailnet(*.ts.net) 호스트에서 접속할 때 Vite 의 Host 체크를 통과시킨다.
    // 마이크/카메라(getUserMedia)는 보안 컨텍스트(HTTPS)에서만 열리므로, 원격/모바일에서
    // 면접 음성·영상을 쓰려면 http LAN/Tailscale IP 가 아니라 https://<machine>.ts.net 로 붙어야 한다.
    allowedHosts: ['.ts.net'],
    proxy: {
      // Forward API calls to the Spring Boot backend during development.
      // Browser hits localhost:5173/api/*, Vite proxies to VITE_PROXY_TARGET (기본 localhost:8080).
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
        // tailnet HTTPS(https://<machine>.ts.net) 로 접속하면 브라우저가 Origin 을 붙여 보내,
        // 백엔드 CORS 필터가 403(Invalid CORS request)로 막는다. /api 는 프록시로 same-origin
        // 전달되므로 Origin 헤더를 제거해 백엔드가 동일 출처 요청으로 처리하게 한다.
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        },
      },
    },
  },

  build: {
    sourcemap: false,
    chunkSizeWarningLimit: 1100,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            if (id.includes('/src/features/community/')) return 'community'
            if (id.includes('/src/features/collaboration/')) return 'collaboration'
            return undefined
          }
          if (id.includes('/@tiptap/') || id.includes('/prosemirror-')) return 'editor-vendor'
          if (id.includes('/@capacitor/') || id.includes('/@mediapipe/') || id.includes('/@heygen/')) return 'mobile-vendor'
          if (id.includes('/lucide-react/') || id.includes('/@radix-ui/') || id.includes('/sonner/')) return 'ui-vendor'
          if (id.includes('/recharts/') || id.includes('/d3-')) return 'charts-vendor'
          return 'vendor'
        },
      },
    },
  },

  // File types to support raw imports. Never add .css, .tsx, or .ts files to this.
  assetsInclude: ['**/*.svg', '**/*.csv'],
  }

  return config
})
