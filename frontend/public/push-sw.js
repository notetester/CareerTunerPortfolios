/*
 * Web Push 핸들러 — generateSW(workbox) 가 만든 서비스워커에 importScripts 로 합쳐진다.
 * (vite.config.ts workbox.importScripts: ['push-sw.js'])
 *
 * 백엔드(DefaultPushSender#buildPayload)는 JSON {title, body, url} 페이로드를 보낸다.
 * - activate      : 이전 SW가 /Obsidian/*를 SPA로 잘못 연 탭을 정적 페이지로 다시 연다.
 * - push          : 알림 배너 표시
 * - notificationclick : 이미 열린 탭이 있으면 포커스, 없으면 url 로 새 창
 * 페이로드 파싱 실패에도 무해하게 기본 문구로 표시한다.
 */
const OBSIDIAN_PATH_PATTERN = /\/Obsidian(?:\/|$)/;

self.addEventListener('activate', (event) => {
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      clientList
        .filter((client) => {
          try {
            return OBSIDIAN_PATH_PATTERN.test(new URL(client.url).pathname);
          } catch (_e) {
            return false;
          }
        })
        .forEach((client) => {
          // activate 완료를 navigation 완료에 묶으면 양쪽이 서로 기다릴 수 있다.
          client.navigate(client.url).catch(() => {});
        });
    }),
  );
});

self.addEventListener('push', (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch (_e) {
    payload = { body: event.data ? event.data.text() : '' };
  }

  const title = payload.title || 'CareerTuner';
  // 서브패스 배포(/CareerTunerDemo/ 등)에서 루트 절대경로는 404 — SW 등록 스코프 기준으로 해석한다.
  const scope = self.registration.scope; // 예: https://host/CareerTunerDemo/
  const url = new URL(payload.url || '.', scope).href;
  const options = {
    body: payload.body || '',
    icon: new URL('icons/icon-192.png', scope).href,
    badge: new URL('icons/favicon-32.png', scope).href,
    data: { url },
    // 같은 알림이 연속 도착하면 배너를 덮어쓴다(스팸 방지).
    tag: payload.tag || 'careertuner',
    renotify: false,
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const targetUrl = (event.notification.data && event.notification.data.url) || self.registration.scope;

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        // 이미 앱이 열려 있으면 그 탭으로 이동/포커스.
        if ('focus' in client) {
          client.navigate(targetUrl).catch(() => {});
          return client.focus();
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(targetUrl);
      }
      return undefined;
    }),
  );
});
