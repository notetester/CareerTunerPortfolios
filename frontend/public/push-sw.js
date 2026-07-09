/*
 * Web Push 핸들러 — generateSW(workbox) 가 만든 서비스워커에 importScripts 로 합쳐진다.
 * (vite.config.ts workbox.importScripts: ['push-sw.js'])
 *
 * 백엔드(DefaultPushSender#buildPayload)는 JSON {title, body, url} 페이로드를 보낸다.
 * - push          : 알림 배너 표시
 * - notificationclick : 이미 열린 탭이 있으면 포커스, 없으면 url 로 새 창
 * 페이로드 파싱 실패에도 무해하게 기본 문구로 표시한다.
 */
self.addEventListener('push', (event) => {
  let payload = {};
  try {
    payload = event.data ? event.data.json() : {};
  } catch (_e) {
    payload = { body: event.data ? event.data.text() : '' };
  }

  const title = payload.title || 'CareerTuner';
  const url = payload.url || '/';
  const options = {
    body: payload.body || '',
    icon: '/icons/icon-192.png',
    badge: '/icons/favicon-32.png',
    data: { url },
    // 같은 알림이 연속 도착하면 배너를 덮어쓴다(스팸 방지).
    tag: payload.tag || 'careertuner',
    renotify: false,
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const targetUrl = (event.notification.data && event.notification.data.url) || '/';

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
