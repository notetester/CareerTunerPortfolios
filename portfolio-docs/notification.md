# 알림 (인앱 · Web Push · FCM)

CareerTuner의 알림 도메인은 여러 기능 모듈(AI 분석, 면접, 첨삭, 커뮤니티, 결제, 문의)에서 발생하는 사건을 사용자에게 **인앱 알림**과 **푸시 알림**으로 전달합니다. 알림 생성의 단일 진입점(`NotificationService.notify`)을 두어 호출부는 도메인 이벤트만 발행하고, 저장·카테고리 필터링·방해금지·기기 발송 같은 전달 정책은 알림 모듈이 일괄 책임집니다.

푸시는 항상 보조 채널로 취급합니다. 인앱 알림은 DB에 먼저 저장되어 사용자가 나중에라도 반드시 확인할 수 있고, 외부 푸시(Web Push·FCM) 발송은 실패하거나 미설정이어도 원래의 알림 생성 흐름을 끊지 않도록 best-effort로 처리합니다.

## 주요 기능

- 크로스모듈 인앱 알림 생성 — 결제·커뮤니티·AI 분석·문의 등 여러 도메인이 공용 진입점으로 알림 발행
- 인앱 알림 목록·미읽음 배지·읽음/전체읽음 처리 (프런트는 폴링 + 토스트로 실시간성 확보)
- Web Push(VAPID) — 브라우저/PWA 서비스워커 구독으로 백그라운드 푸시
- 네이티브 FCM 푸시 — Capacitor 앱의 디바이스 토큰으로 발송(APNs도 FCM 경유)
- 알림 설정 — 푸시/이메일 on·off, 카테고리별 on·off, 방해금지 시간대(KST)
- 발송기 미설정 시 로깅 폴백 — VAPID 키·FCM 서비스계정이 없어도 서비스가 정상 기동

## 핵심 구현

### 단일 진입점과 커밋 후 비동기 푸시

모든 알림은 `NotificationServiceImpl.notify(Notification)` 한 곳으로 모입니다. 이 메서드는 `NotificationMapper.insert`로 인앱 알림을 저장한 뒤, 곧바로 발송하지 않고 `ApplicationEventPublisher`로 `NotificationPushEvent`만 발행합니다.

`NotificationPushListener`는 이 이벤트를 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("notificationExecutor")`로 수신합니다.

- `AFTER_COMMIT`: 알림 insert 트랜잭션이 커밋된 뒤에만 발송 → 롤백된 알림이 푸시되는 "유령 푸시"를 방지
- `fallbackExecution = true`: 트랜잭션 없이 `notify()`가 호출된 경로에서도 리스너가 실행되어 푸시 유실 방지
- `@Async`: 발송을 전용 스레드 풀로 분리 → 결제 등 **호출자 트랜잭션·웹 스레드가 외부 푸시 HTTP에 묶이지 않음**

전용 풀은 `NotificationAsyncConfig`의 `notificationExecutor` 빈(core 2 / max 4 / queue 200)으로, 큐 포화 시 폐기 대신 `CallerRuns`로 폴백해 발송 유실을 줄이고, 재배포 시 진행 중 발송을 마치고 종료합니다(`WaitForTasksToCompleteOnShutdown`).

### 전달 정책 게이팅 (PushDispatcher)

`PushDispatcher.dispatch`는 알림 1건을 사용자의 등록 기기로 보내기 전, 여러 조건을 순서대로 확인하고 하나라도 걸리면 조용히 건너뜁니다.

1. 사용자 알림 설정에서 `pushEnabled`가 꺼져 있으면 중단
2. `NotificationCategories.of(type)`로 알림 타입을 카테고리로 매핑하고, 해당 카테고리가 off면 중단
3. 지금(KST)이 사용자의 **방해금지 시간대** 안이면 중단 — 단, 인앱 알림은 이미 저장돼 있어 나중에 확인 가능

방해금지 판정(`isWithinQuietHours`)은 `[start, end)` 반열림 구간이며, `22:00~07:00`처럼 **자정을 넘기는 구간**도 처리합니다. 미설정·형식 오류·`start==end`는 "방해금지 없음"으로 보아 안전하게 발송을 허용합니다. 모든 예외는 삼켜 인앱 알림 생성에 영향을 주지 않습니다.

### 채널 라우팅과 발송 클라이언트

`DefaultPushSender`(`@Primary`)가 구독의 `kind`로 실제 채널을 고릅니다.

- `WEB` → `VapidWebPushClient`: `nl.martijndwars.webpush` 라이브러리로 브라우저 구독(endpoint + p256dh + auth)에 암호화 페이로드(JSON: title/body/url) 전송. BouncyCastle Provider를 런타임 등록합니다.
- `FCM` / `APNS` → `FcmPushClient`: Firebase Admin SDK로 디바이스 토큰에 발송. Android/Webpush 설정에 클릭 이동 `url`을 심습니다.

두 클라이언트는 **키/서비스계정이 있을 때만 실제 발송**하도록 조건부로 동작합니다. `VapidWebPushClient`는 `@ConditionalOnProperty(prefix = "careertuner.push.vapid", name = {"public-key","private-key"})`로 키가 있을 때만 빈이 생성되고, `FcmPushClient`는 항상 생성되지만 서비스계정 로드 실패 시 `isReady()=false`로 degrade합니다. 발송기가 없으면 `LoggingPushSender`로 폴백해 개발 환경에서도 흐름이 끊기지 않습니다.

### 기기 구독 등록 (프런트 어댑터)

프런트 `platform/push.ts`가 웹/네이티브 구독을 백엔드 `POST /api/notifications/push`로 등록합니다.

- 웹/PWA: `Notification.requestPermission()` → 서비스워커(`public/push-sw.js`) `pushManager.subscribe`에 VAPID 공개키(`VITE_VAPID_PUBLIC_KEY`, 개발 기본값 내장)를 전달 → `{ kind: "WEB", token: endpoint, p256dh, auth }` 등록
- 네이티브: 런타임 Capacitor `PushNotifications` 플러그인이 있으면 registration 리스너로 FCM 토큰을 받아 `{ kind: "FCM", token }` 등록
- 키/플러그인 미설정: 권한 요청까지만 동작하고 구독 생성은 보류(`permission-only`)해 무해하게 degrade

서버는 `PushSubscriptionMapper.upsert`로 `push_subscription`에 `ON DUPLICATE KEY UPDATE` 저장(동일 토큰 재등록 시 갱신)하고, 구독 해제는 endpoint/token 기준 삭제입니다.

### 인앱 실시간성 (폴링 + 토스트)

인앱 알림의 실시간 갱신은 SSE가 아닌 **주기 폴링**으로 구현했습니다. 프런트 `useNotificationStore`(zustand)가 `pollNotifications`에서 목록·미읽음 수를 다시 받아, `lastNotifiedId`보다 큰 새 미읽음 알림만 골라 토스트로 띄웁니다. 최초/수동 로드는 기준선(`lastNotifiedId`)만 끌어올려 과거 알림이 한꺼번에 토스트로 쏟아지는 것을 막습니다.

## 설계 결정과 트레이드오프

- **인앱 우선, 푸시는 보조**: 알림을 먼저 DB에 저장한 뒤 푸시하므로, 푸시 미도달·미설정 상황에서도 사용자가 알림을 잃지 않습니다. 대신 실시간 도달은 폴링 주기에 의존합니다.
- **SSE 대신 폴링**: 알림은 커뮤니티 챗봇·에이전트 타임라인 같은 스트림형 UX와 달리 갱신 빈도가 낮아, 별도 SSE 커넥션 유지 비용보다 폴링이 단순하고 인프라 부담이 적다고 판단했습니다. (SSE는 AutoPrep·면접 에이전트 등 스트리밍이 필요한 다른 도메인에서 사용합니다.)
- **AFTER_COMMIT + 전용 비동기 풀**: 결제 같은 호출자 트랜잭션이 외부 푸시 HTTP 지연에 묶이지 않게 하고, 롤백 시 유령 푸시를 막습니다. 대신 발송이 커밋 이후 비동기라 극히 짧은 지연이 생깁니다.
- **조건부 빈 + 로깅 폴백**: VAPID 키나 FCM 서비스계정이 없어도 애플리케이션이 기동하고, 설정을 넣는 순간 실제 발송이 자동 활성화됩니다. 개발/데모 환경과 운영 환경을 코드 변경 없이 같은 코드로 커버합니다.
- **best-effort 예외 격리**: 푸시 경로의 모든 예외는 삼켜 인앱 알림 생성 흐름을 보호합니다. 트레이드오프로 개별 푸시 실패는 사용자에게 즉시 노출되지 않고 로그로만 남습니다.

## 데이터 · 연동

- 테이블: `notification`(인앱 알림, actor JOIN 포함), `notification_preference`(설정 — 카테고리 on/off는 JSON 컬럼, 방해금지 시간대), `push_subscription`(기기 구독 — kind/token/p256dh/auth)
- 외부 연동: Web Push(VAPID, `nl.martijndwars.webpush` + BouncyCastle), Firebase Cloud Messaging(Firebase Admin SDK; APNs는 FCM 경유)
- 설정 키: `careertuner.push.vapid.{public-key, private-key, subject}`, `careertuner.push.fcm.service-account` (모두 env 오버라이드, 미설정 시 로깅 폴백)
- 발행처(크로스모듈): `billing`, `community/moderation`, `fitanalysis`, `applicationcase`(추출 워커), `admin/ticket` 등이 공용 진입점 `NotificationService.notify`로 알림을 생성
- 카테고리 매핑: `NotificationCategories`가 알림 타입을 `ai_analysis / interview / correction / community / billing / notice` 6개 사용자 카테고리로 매핑(프런트 `TYPE_TO_CATEGORY`와 동일 기준)

## 사용 기술

- 백엔드: Spring Boot 4 / Java 21, MyBatis, MySQL 8, `ApiResponse<T>` envelope
- 이벤트/비동기: `ApplicationEventPublisher`, `@TransactionalEventListener(AFTER_COMMIT)`, `@Async` + `ThreadPoolTaskExecutor`
- 푸시: `nl.martijndwars.webpush`(VAPID) + BouncyCastle, Firebase Admin SDK(FCM/APNs)
- 프런트: React 19 / TypeScript, zustand 스토어, Service Worker `pushManager`, Capacitor `PushNotifications`
