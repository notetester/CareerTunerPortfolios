# 개인 차단/허용 정책 (Personal Block Policy)

사이트(운영자) 차원의 제재와 별개로, **사용자 개인이** 누구로부터 무엇을 받을지·무엇을 볼지 정하는 시스템.
목표: 세부 옵션을 하나도 없애지 않으면서, 사용자가 헷갈리지 않게 만드는 것.

## 1. 핵심 구조 — 3층 모델

조합 폭발(관계 5종 × 표면 25종 × 방유형 × 개설/속함 × 익명 = 수백 옵션)을 **상속 + 오버라이드**로 해결한다.
모든 세부 옵션은 존재하되, 값이 없으면(`null`) 상위 규칙을 따른다. UI 는 "상위 설정 따름(현재: 허용)"으로 표기한다.

```
[1층] 대상 차단 목록      : "누구를"  — 계정 / IP / 채팅방 단위. 항목마다 표면별 세부 토글.
[2층] 관계별 기본 정책     : "어떤 관계로부터 무엇을" — 모르는 사람/친구/기업/차단한 계정/차단한 IP 별 표면 매트릭스.
[3층] 시스템 기본값        : stranger·friend·company = 허용, blockedAccount·blockedIp = 차단.
```

### 평가 우선순위 (구체적인 것이 이긴다)
```
① 개별 계정 차단 항목의 명시적 표면 설정 (user_block.flags_json 의 non-null 값)
② IP 차단 매치 → blockedIp 관계 정책
③ 채팅방 차단 파생 규칙 (conversation_block.flags_json)
④ 관계별 정책 (표면 키의 상속 사슬: 구체 → 상위)
⑤ 시스템 기본값
```

## 2. 표면(Surface) 카탈로그 — 평면 키 + 점(.) 상속

표면 키는 점 표기로 상속된다. `invite.GROUP.creator.anonymous` 값이 없으면
`invite.GROUP.creator` → `invite.GROUP` → `invite` 순으로 올라가며 첫 non-null 을 쓴다.

| 카테고리 | 키 | 의미 |
| --- | --- | --- |
| 다이렉트 | `dm` | 1:1 채팅 시작/수신 |
| | `note` | 쪽지 수신 |
| | `friendRequest` | 친구 요청 수신 |
| | `fileShare` | 파일 공유 수신 |
| | `postingShare` | 공고 공유 수신 |
| 초대 | `invite` | 모든 채팅방 초대 (베이스) |
| | `invite.{GROUP\|PUBLIC\|PRIVATE}` | 방 유형별 |
| | `invite.{T}.{creator\|member}` | 초대자가 그 방의 개설자인지/참여자인지 |
| | `invite.{T}.{S}.anonymous` | 익명 초대 (초대자가 익명으로 표시되는 초대) |
| 콘텐츠 노출 | `content.post` / `content.post.anonymous` | 게시글 / 익명 게시글 |
| | `content.comment` / `content.comment.anonymous` | 댓글 / 익명 댓글 |
| | `content.reply` / `content.reply.anonymous` | 답글 / 익명 답글 |
| | `content.roomMessage` / `content.roomMessage.anonymous` | 채팅방 내 그 사람의 메시지 노출 |
| | `content.allAnonymousRoomMessages` | 방 전체 익명 메시지 노출 (방 개설자 관계 기준) |
| 프로필 | `profile.viewMe` | 상대가 내 프로필 조회 |
| | `profile.visibleToMe` | 상대 프로필이 나에게 노출 |
| | `profile.searchMe` | 상대의 검색 결과에 내 계정 노출 |

관계 클래스: `stranger` `friend` `company` `blockedAccount` `blockedIp` — **각각 완전히 독립**.
(운영자(operator)는 제재·공지 수단이라 개인 정책의 대상이 아니다.)

## 3. 대상 차단 목록

- **계정 차단** (`user_block`): 기본 = 전 표면 차단. 항목별 시트에서 표면 단위로 완화 가능
  (예: 유저2는 1:1채팅만 차단하고 쪽지는 허용). `flags_json` 의 non-null 값이 최우선.
- **IP 차단** (`user_ip_block`): 원본 IP 는 절대 노출하지 않는다. 차단한 계정 시트의
  "이 계정의 접속망(IP)도 차단" 토글로 파생 생성 — 서버가 최근 로그인 IP 를 해시(SHA-256+서버솔트)로 저장.
  같은 IP 해시로 접속한 다른 계정은 `blockedIp` 관계로 평가된다(스팸 부계정 선제 차단).
  목록에는 "차단 IP #n — ○○ 계정에서 파생 · 현재 계정 k개 일치" 형태로만 표기.
- **채팅방 차단** (`conversation_block`): 방 숨김 + 그 방 관련 초대 차단. 파생 규칙(각각 토글):
  `inviteFromRoom`(그 방으로의 재초대, 기본 차단) / `.anonymous` /
  `memberCreatedRoomInvite`(그 방 구성원이 **개설한** 다른 방 초대) / `.anonymous` /
  `memberJoinedRoomInvite`(그 방 구성원이 **속한** 다른 방 초대) / `.anonymous`
  → 연속 초대 테러 차단용.

## 4. UI/UX — "간편 → 고급 → 개별" 3단 (SNS 벤치마킹)

1. **수신 범위 (간편)** — TikTok/Instagram 식 레벨 셀렉터. 표면마다
   `모두 / 친구·기업만 / 친구만 / 아무도` 하나만 고르면 내부적으로 관계 토글 묶음으로 저장.
   대부분의 사용자는 이 단계에서 끝난다.
2. **고급 매트릭스** — 카테고리 아코디언(다이렉트/초대/콘텐츠/프로필) × 관계 5열 그리드.
   초대 셀은 펼치면 방유형×개설/속함×익명 세부 행이 나오고, 기본 상태는 "상위 따름(현재값 표시)".
   X(트위터)의 뮤트·차단 분리처럼, 콘텐츠 노출 차단은 상대가 모르게 조용히 적용된다.
3. **개별 차단 시트** — Facebook 식 하드블록이 기본이되, 항목을 열면 표면별 스위치로 완화.
   차단 진입점: 메신저 사용자 메뉴 · 커뮤니티 글/댓글 작성자 메뉴 · 유저 검색 결과.

원칙: **조용한 실패(silent deny)** — 차단당한 쪽에는 차단 사실을 알리지 않는다
(DM 시작 실패는 일반 오류 문구, 콘텐츠는 톰스톤 "차단한 사용자의 글입니다" 또는 완전 숨김 선택).

## 5. 집행 지점 (서버측 — 3플랫폼 공통 적용)

| 표면 | 지점 |
| --- | --- |
| dm/note | `CollaborationServiceImpl.openDirectConversation` / `sendMessage(NOTE)` |
| friendRequest | `sendFriendRequest` |
| invite.* | `inviteMembers` / `joinConversation` 초대 생성 시 (차단된 초대는 조용히 스킵) |
| content.roomMessage | `listMessages` 응답에서 뷰어 기준 톰스톤 처리 |
| content.post/comment/reply | 커뮤니티 목록/상세 조회에서 뷰어 기준 필터 |
| profile.* | 유저 검색 / 프로필 조회 |
| 알림 억제 | `NotificationServiceImpl.notify` — 발신자가 차단 관계면 알림 자체를 생성하지 않음 |

## 6. API

```
GET  /api/privacy/policy                  관계별 정책 문서 (기본값 병합 완료 상태)
PUT  /api/privacy/policy                  부분 갱신 (null=상위 따름 유지)
GET  /api/privacy/blocks/users            차단 계정 목록(+표면 플래그, IP 파생 여부)
POST /api/privacy/blocks/users            {targetUserId, blockIp?} 차단 생성
PUT  /api/privacy/blocks/users/{id}       표면 플래그/blockIp 수정
DELETE /api/privacy/blocks/users/{id}     차단 해제(파생 IP 차단도 함께 해제)
GET/DELETE /api/privacy/blocks/ips        IP 차단 목록/해제 (원본 IP 비노출)
GET/POST/PUT/DELETE /api/privacy/blocks/conversations   채팅방 차단
```

## 7. 확장 여지 (이번 릴리스 범위 밖, 슬롯만 존재)

- 기간 차단(N일 후 자동 해제), 뮤트(soft) vs 차단(hard) 분리 뱃지, 차단 사유 메모 검색,
  IP 대역(CIDR) 차단, 차단 목록 내보내기/가져오기, 신고와 연동(차단 시 신고 제안).
