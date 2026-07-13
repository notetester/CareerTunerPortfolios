# 결제 · 크레딧 · 요금제

CareerTuner의 유료화 계층은 세 축으로 나뉩니다. **구독 요금제(subscription plan)** 는 매 결제 주기마다 기능별 사용권(benefit ticket)을 지급하고, **크레딧 지갑(credit wallet)** 은 사용권을 소진했거나 정책상 티켓 대상이 아닌 AI 호출을 종량으로 정산하며, **결제(payment)** 는 Toss Payments 승인으로 이 둘을 충전합니다. AI 기능이 실행될 때는 먼저 사용권을 차감하고, 부족하면 정책에 따라 크레딧으로 폴백하는 단일 진입점(`AiChargeService`)이 과금을 처리합니다.

정책(요금제 가격·사용권 수량·크레딧 단가)은 코드 상수가 아니라 DB 테이블로 관리되며, 관리자가 **예약 변경(policy change)** 을 걸면 조회 시점에 유효 시점 기준으로 스냅샷이 적용됩니다. 사용자가 구독을 시작하면 그 시점의 정책이 `policy_snapshot_json`으로 고정되어, 나중에 요금제가 바뀌어도 진행 중인 구독 기간의 혜택은 흔들리지 않습니다.

## 주요 기능

- 요금제 목록·기능별 사용권 정책·크레딧 상품 조회 (비로그인 포함 공개)
- 내 구독/크레딧 잔액, 결제 내역, 이번 달 사용량, 사용권 잔량·거래 내역 조회
- 구독 시작·해지(해지는 기간 만료까지 유지 후 자동 갱신 중단)
- Toss Payments 결제창 연동: 결제 준비(ready) → 승인(confirm) → 취소(cancel) 3단계
- AI 호출 과금: 사용권 우선 차감, 초과분 정책 기반 크레딧 폴백, 멱등 처리
- 관리자: 결제 조회·집계, 요금제/상품/정책 조회, 정책 예약 변경 및 취소

## 핵심 구현

### 사용권 우선 · 크레딧 폴백 과금 (`AiChargeServiceImpl`)

AI 기능 1회 실행의 과금은 `AiChargeService.charge(AiChargeCommand)`가 트랜잭션 안에서 단독으로 결정합니다. 순서는 다음과 같습니다.

1. `activeFeatureBenefitPolicy(userId, featureType)`로 해당 기능의 사용권 정책을 조회합니다. 티켓 대상이 아니면(`isIncludedInTicket() == false`) 곧바로 크레딧 차감으로 넘어갑니다.
2. 티켓 대상이면 `AiBenefitUsageService.consumeByFeature(...)`로 사용권 1장을 차감합니다. 성공하면 `AiChargeResult.ticket(...)`을 반환합니다.
3. 사용권이 소진되어 `INSUFFICIENT_CREDIT`가 나면, 구독 혜택 정책의 `overagePolicy`가 `CREDIT`/`FALLBACK_CREDIT`일 때만 크레딧 차감으로 폴백합니다. 폴백이 허용되지 않으면 예외를 그대로 전파합니다.

크레딧 단가는 요청에서 명시한 값(`command.creditCost()`)이 최우선이고, 없으면 구독 혜택 정책의 `creditCost`, 그다음 기능 정책의 `defaultCreditCost` 순으로 결정합니다(`AiChargeServiceImpl#creditCost`). 과금 결과는 `AiChargeResult`(`TICKET`/`CREDIT`/`SKIPPED`) 한 타입으로 통일되어, 호출부가 티켓·크레딧 어느 경로로 정산됐는지 남은 잔량과 함께 알 수 있습니다.

> 이 과금 서비스와 사용권 차감(`consumeByFeature`), 크레딧 차감(`deductByAiUsageLog`)은 AI 도메인이 공통으로 호출하도록 설계된 진입점입니다. 공개 포트폴리오 코드 기준으로는 `billing`/`credit` 패키지 내부에서만 참조되며, 각 AI 기능 도메인에서의 실제 호출 연결은 이 저장소 범위에 포함되어 있지 않습니다.

### `ai_usage_log` 기반 멱등 크레딧 차감 (`CreditServiceImpl`)

크레딧 차감은 항상 하나의 `ai_usage_log` 레코드에 귀속됩니다(`deductByAiUsageLog(aiUsageLogId, creditUsed)`). AI 사용량을 먼저 로깅한 뒤 그 로그 ID로 차감을 호출하는 구조여서, 사용 집계와 과금 원장이 1:1로 맞습니다.

- 로그 상태가 `SUCCESS`가 아니면 차감하지 않고 `NOT_SUCCESS`로 스킵합니다. 실패한 AI 호출에는 과금하지 않습니다.
- 같은 `ai_usage_log_id`·`AI_USAGE` 유형의 거래가 이미 있으면 `ALREADY_DEDUCTED`로 스킵합니다(재시도·중복 요청 방어).
- 실제 차감은 `deductUserCreditIfEnough`로 수행하는데, 이 UPDATE의 갱신 행 수가 0이면 잔액 부족으로 판단해 `INSUFFICIENT_CREDIT`를 던집니다. **조건부 UPDATE로 잔액 검사와 차감을 한 쿼리에 묶어 경쟁 조건을 막습니다.**
- 성공 시 `credit_transaction`에 음수 금액과 차감 후 잔액(`balanceAfter`)을 기록합니다.

### Toss Payments 결제 흐름 (`PaymentServiceImpl`, `TossPaymentClient`)

실결제는 `/api/payments/toss` 하위의 ready → confirm → cancel 3단계입니다(`PaymentController`).

- **ready**: 상품 유형(`CREDIT`/`SUBSCRIPTION`)에 따라 서버가 금액·크레딧 수량을 확정한 `payment` 행을 `READY` 상태로 만들고, 충돌 대비 재시도(`insertPaymentWithUniqueOrderId`, 최대 3회)로 유니크한 `orderId`를 발급합니다. 결제 시점의 정책은 `policy_snapshot_json`으로 함께 저장합니다.
- **confirm**: 소유자·금액·상태·`paymentKey` 중복을 검증한 뒤 `TossPaymentClient.confirm`으로 Toss 승인 API를 호출합니다. 응답의 `paymentKey`/`orderId`/`totalAmount`/`status == "DONE"`를 서버가 저장한 값과 재대조해 위변조를 막습니다. 통과하면 `markPaidIfReady`(READY→PAID 조건부 UPDATE)로 상태를 올리고, 구독이면 `activateSubscriptionAfterPayment`, 크레딧이면 `grantCreditsAfterPayment`를 호출합니다.
- 승인 요청에는 `Idempotency-Key` 헤더로 `orderId`를 실어 Toss 측 재요청도 안전하게 처리합니다. 인증은 시크릿 키 뒤에 콜론을 붙여 Base64 인코딩한 Basic 헤더입니다(키 값은 `careertuner.toss.payments` 설정에서 주입).
- **confirm 재시도 방어**: `markPaidIfReady`의 갱신 행 수가 0이면 이미 PAID된 건인지 재확인하고, 그렇다면 기존 결과를 그대로 응답해 중복 충전을 막습니다.

### 개발용 즉시 결제 vs 실결제 (`BillingServiceImpl`)

`BillingServiceImpl`의 `subscribe`/`purchaseCredits`는 외부 PG 없이 결제를 즉시 `PAID`로 기록하는 개발용 경로입니다(`provider = "DEV"`). 실제 PG 연동 경로(`PaymentServiceImpl`)와 병존하며, 두 경로 모두 최종적으로 `activateSubscription`/`grantCreditsAfterPayment`라는 동일한 상태 반영 지점으로 수렴합니다. 이 분리 덕분에 UI·구독 로직을 PG 없이도 개발·시연할 수 있습니다.

구독 시작 시 `ensureBalances`가 요금제의 사용권 정책을 읽어 `user_benefit_balance`를 그 주기만큼 생성하고, 지급 내역을 `benefit_transaction`에 `GRANT`로 남깁니다. 동시 요청으로 같은 잔액이 중복 생성되는 경우는 `DuplicateKeyException`을 삼켜 무시합니다.

### 무료 티어와 구독 해지

무료(`FREE`) 사용자는 활성 구독이 없어도 사용권을 받습니다. `currentBenefitPeriod`가 활성 구독이 없으면 "이번 달 1일 ~ 다음 달 1일"을 사용권 주기로 잡고, `ensureBalances`가 요금제 정책에 정의된 FREE 사용권을 그 달치로 지급합니다. `subscribe`에 `FREE`가 들어오면 이를 **구독 해지 요청으로 간주**합니다.

해지(`cancelSubscription`)는 즉시 중단이 아니라 `cancelActiveSubscription`으로 자동 갱신만 끄는 방식이라, 이미 결제한 기간(`current_period_end`)까지는 유료 혜택이 유지됩니다. 해지 시 만료일을 담은 알림을 발송합니다.

### 관리자 정책 예약 변경 (`BillingPolicyChangeService`)

가격·사용권·크레딧 단가 변경은 즉시 반영이 아니라 **예약 후 유효 시점 적용** 구조입니다. 관리자가 `POST /api/admin/plans/policy-changes`로 대상 유형(요금제/크레딧 상품/구독 혜택/AI 기능 혜택)과 다음 스냅샷·적용 시점(`effectiveFrom`)·적용 방식을 등록하면 `SCHEDULED` 상태로 저장됩니다. 조회 계층(`BillingPolicyService`)이 요금제·상품을 읽을 때 `applyEffective...Change`로 유효 시점이 지난 예약을 스냅샷에 얹어 반환하므로, 진행 중인 구독은 시작 시 고정된 스냅샷을 그대로 쓰고 신규 구매·다음 주기부터 새 정책이 적용됩니다. 적용 방식 기본값은 대상별로 다릅니다(크레딧 상품 `NEW_PURCHASE_FROM_EFFECTIVE_AT`, 요금제 `NEXT_SUBSCRIPTION_PERIOD`, 그 외 `NEXT_BENEFIT_PERIOD`).

### AI 실행 preview와 요청 단위 정산

AI 기능은 실행 전에 사용할 사용권, 부족 시 예상 크레딧, 현재 잔액을 preview합니다. 사용자가 확인한 뒤 실행하고 성공 결과에만 실제 사용량을 정산합니다. action/request key를 원장에 묶어 timeout·재시도·중복 클릭이 같은 첨삭이나 분석을 두 번 차감하지 못하게 합니다.

사용권이 있으면 먼저 소비하고, 부족하면 정책에 따라 크레딧으로 폴백합니다. 결과 저장과 정산 결과가 애매한 네트워크 실패에서는 무조건 새 결제를 만들지 않고 기존 요청 상태를 조회해 reconciliation합니다.

### 요금제 추천

`/api/billing/plan-recommendation`은 사용량과 목적을 규칙 기반으로 비교해 추천 플랜과 근거를 반환합니다. LLM 설명이 없어도 결정이 재현되며, 화면은 추천을 자동 구매로 처리하지 않고 사용자가 플랜을 비교한 뒤 선택하게 합니다.

## 설계 결정과 트레이드오프

- **사용권 우선, 크레딧 보조.** AI 과금은 구독으로 받은 사용권을 먼저 쓰고, 초과분만 `overagePolicy`가 허용할 때 크레딧으로 정산합니다. 사용자는 예측 가능한 정액 혜택을, 서비스는 초과 사용에 대한 종량 안전판을 얻습니다. 대신 과금 경로 분기(티켓/폴백/스킵)가 늘어 로직이 복잡해지는 비용을 감수했습니다.
- **원장은 `ai_usage_log`에 못박는다.** 크레딧 차감은 반드시 사용량 로그 ID를 통해서만 일어나 사용 집계와 과금이 어긋나지 않고, `ai_usage_log_id` 기준 멱등 검사로 중복 과금을 원천 차단합니다.
- **정책은 DB + 스냅샷.** 가격·혜택을 코드가 아닌 테이블로 두고, 구독 시작 시점 정책을 `policy_snapshot_json`으로 고정합니다. 운영 중 정책을 바꿔도 진행 중 구독의 혜택이 소급 변경되지 않습니다. 대신 "현재 정책"과 "스냅샷 정책"이라는 두 관점을 코드가 모두 다뤄야 합니다.
- **경쟁 조건은 조건부 UPDATE로.** 잔액 차감(`deductUserCreditIfEnough`)과 상태 전이(`markPaidIfReady`, `consumeBenefitIfEnough`)를 모두 "조건을 만족할 때만 갱신"하는 UPDATE로 처리하고 갱신 행 수로 성공을 판정해, 애플리케이션 레벨 락 없이 동시성을 방어합니다.
- **DEV 결제와 실결제의 병존.** 외부 PG 없이도 전체 흐름을 시연할 수 있도록 `provider = "DEV"` 즉시 결제 경로를 남겨두되, 상태 반영 지점을 실결제와 공유해 두 경로의 결과가 갈라지지 않게 했습니다. 이는 포트폴리오·데모 환경을 위한 절충이며, 프로덕션에서는 Toss 경로만 사용하는 것을 전제로 합니다.

## 데이터 · 연동

주요 테이블 (mapper: `resources/mapper/billing/`의 `BillingMapper.xml`·`BillingPolicyChangeMapper.xml`, 크레딧·결제는 각각 `credit`/`payment` 패키지 매퍼):

| 테이블 | 용도 |
| --- | --- |
| `subscription_plan` | 요금제 정의(코드·월/연 가격·정렬) |
| `subscription_benefit_policy` | 요금제별 기능 사용권 정책(수량·리셋 주기·`overagePolicy`·크레딧 단가) |
| `ai_feature_benefit_policy` | AI 기능별 티켓 포함 여부·기본 크레딧 단가 |
| `user_subscription` | 사용자 활성 구독·기간·`policy_snapshot_json` |
| `user_benefit_balance` | 주기별 사용권 잔량(지급/사용/잔여) |
| `benefit_transaction` | 사용권 지급(`GRANT`)·차감(`CONSUME`) 원장 |
| `credit_product` | 크레딧 충전 상품(가격·지급 수량) |
| `credit_transaction` | 크레딧 충전·차감 원장(`AI_USAGE`, `CHARGE` 등) |
| `payment` | 결제 건(provider·상태·orderId·paymentKey·스냅샷) |
| `ai_usage_log` | AI 사용량 로그(과금의 기준 원장, 공통 테이블) |
| `billing_policy_change` | 정책 예약 변경(대상·현재/다음 스냅샷·유효 시점·적용 방식) |

외부 연동: **Toss Payments** 승인 API(`https://api.tosspayments.com/v1/payments/confirm`)를 `java.net.http.HttpClient`로 직접 호출합니다. 설정은 `careertuner.toss.payments` prefix(`TossPaymentProperties`)로 주입되며, 시크릿 키가 비어 있으면 승인 단계에서 즉시 예외를 던집니다. 프론트는 `@tosspayments` 결제 SDK로 결제창을 띄우고 성공 리다이렉트 후 confirm을 호출합니다.

주요 엔드포인트:

- 사용자: `GET /api/billing/plans`, `/me`, `/payments`, `/usage`, `/benefits/me`, `POST /api/billing/subscribe`, `/credits/purchase`, `/subscription/cancel`
- 결제: `POST /api/payments/toss/{ready,confirm,cancel}`
- 관리자: `GET /api/admin/payments`, `/summary`, `GET /api/admin/plans`, `POST /api/admin/plans/policy-changes`(+`/{id}/cancel`)

## 사용 기술

- **백엔드**: Spring Boot 4 · Java 21 · MyBatis(매퍼 XML) · MySQL 8, `ApiResponse<T>` 응답 봉투, `@Transactional` 경계, `java.net.http.HttpClient` 기반 Toss 연동, Jackson(`tools.jackson`) JSON 처리
- **동시성/정합성**: 조건부 UPDATE + 갱신 행 수 판정, `ai_usage_log_id`/`paymentKey` 기반 멱등 처리, `DuplicateKeyException` 방어
- **프런트엔드**: React 19 · Vite 8 · TypeScript(`features/billing`의 api·types), Toss Payments 결제 SDK, 관리자 SPA(`admin/features/plans`)
