# C 확장 설계 — 근거 조회 + 솔직한 불확실성 시스템 (설계안, 구현 별도)

> 상태: **설계안**. 이번 PR 은 설계까지. 자격증 일정을 MVP 대상으로, 전체 구현은 다음 단계.
> 목적: 3B 가 자격증 시험일정·기업 최신이슈 같은 **시점 정보를 외워서/추측해서** 답하지 않게 하고,
> **확인 가능한 정보만 출처·신선도와 함께 쓰고, 불확실하면 불확실하다고 솔직히** 말하게 한다.

## 0. 왜 (문제의식)

자격증 접수/시험/발표일, 기업 최신 이슈는 3B(심지어 상위 LLM)가 기억하는 정보가 아니다. 상위 LLM 도
검색→근거확인→신선도·출처 표기로 답한다. CareerTuner 도 동일 전략을 취한다. **목표는 완벽한 일정 DB 가
아니라, "확인된 것만 출처와 함께, 불확실한 건 솔직히"** 다.

## 1. 냉정한 정정 3가지 (설계 전제)

1. **B 의 증거 패턴을 미러(재발명 금지).** `company_analysis` 는 이미 `verifiedFacts`·`sources`·`sourceType`·
   `aiInferences`(사실/추론 분리)·`checkedAt`·`refreshRecommendedAt` 로 "출처+신선도+검증사실" 패턴을 갖고 있다.
   자격증 근거도 이 스키마를 미러해 일관성·재사용을 얻는다. 공용 `EvidenceStatus`/`Freshness` 개념을 뽑아
   기업맥락·자격증 양쪽이 쓴다.
2. **C 가 자체 웹검색 파이프라인을 새로 만들지 않는다.** (영역 원칙: C 는 조사 결과를 소비.) `EvidenceResearchService`
   는 **공유/조회 인터페이스**로 두고, 실제 조회는 B 의 websearch 인프라(또는 공식 API)를 재사용/위임한다.
   초기엔 **stub/수동 입력**으로 두고 인터페이스만 확정 → 실 조회는 pluggable.
3. **MVP 는 "조회 인프라"가 아니라 "솔직함 계층"이다.** 상태값 기반 답변 수위 조절 + cert-need-gate + 최소
   카탈로그가 본질. 라이브 웹조회는 뒤로 미룬다.

## 2. 근거 객체 (B 패턴 미러)

```text
EvidenceItem {
  sourceName    // 예: 한국데이터산업진흥원
  sourceUrl     // 공식 페이지 URL
  retrievedAt   // 우리가 조회한 시각
  publishedAt   // 출처가 게시한 시각(있으면)
  value         // 확인된 값(일정/이슈 등)
  confidence    // 신뢰도
  freshness     // FRESH / AGING / STALE (retrievedAt·publishedAt 기반 결정론 산정)
  status        // 아래 EvidenceStatus
}
```

C 는 **이 객체 안의 value 로만** 일정/최신정보를 말한다. 객체가 없으면 일정 단정 금지.

## 3. 조회 상태값 (일정/시점 정보)

| status | 의미 | 답변 수위 |
| --- | --- | --- |
| VERIFIED_CURRENT | 공식 출처에서 최신 일정 확인 | 일정 기반 전략 제공("○○ 공식 페이지 확인 기준…") |
| OFFICIAL_NO_SCHEDULE | 공식 페이지 확인됐으나 일정 미공개 | 일반 학습계획만 |
| STALE_ONLY | 오래된 일정만 확인 | 날짜 단정 금지, 공식 재확인 표시 |
| CONFLICTING | 출처 간 일정 충돌 | 사용자에게 공식 확인 요청 |
| NOT_FOUND | 신뢰 가능한 일정 없음 | 일정 조언 생략, 날짜 만들지 않음 |

답변 예시(VERIFIED_CURRENT): "한국데이터산업진흥원 공식 페이지 확인 기준 SQLD 제○회 접수 ○월○일~○월○일,
시험일 ○월○일. 단 변경될 수 있어 접수 전 공식 재확인 필요. 이 일정이면 공고 마감 전 합격 반영은 어려우니
'SQL 역량 보완 중'/'시험 준비·접수 예정' 표현이 적절." / (NOT_FOUND): "신뢰 가능한 공식 출처에서 일정 미확인.
임의 날짜 계획은 위험하니 공식 확인 후 재계산 권장. 접수일/시험일/발표일 입력 주시면 마감일과 비교해 계산."

## 4. 자격증은 **보조 전략** — 기본 OFF + 조건부 게이트

CareerTuner 본질은 맞춤 취업전략이지 자격증 추천 AI 가 아니다. **항상 추천/언급 금지.**

**CertificateNeedGate (기본 OFF)** — 아래 중 하나일 때만 ON:
1. 공고에 특정 자격증 명시
2. 직무 특성상 자격증이 강하게 요구되거나 법적/제도적으로 중요
3. 부족 역량이 자격증으로 객관화하기 좋음
4. 사용자 보유 자격증이 공고/기업 맥락에서 강점
5. 사용자가 자격증 전략을 명시 요청

**게이트 OFF 면 자격증 카탈로그·일정 컨텍스트를 C 프롬프트에 주입하지 않는다**(프롬프트에 자격증 목록이 들어가면
3B 가 과대반영). 게이트 ON 인 후보에 대해서만 `CertificateResearchService` 가 일정 조회.

## 5. 자격증 전략 결과 = 상태값 (추천 목록 아님)

| status | 의미 |
| --- | --- |
| NOT_NEEDED | 현 시점 자격증 전략 불필요 (**정상 결과**) |
| USE_EXISTING_AS_STRENGTH | 보유 자격증을 어필 |
| OPTIONAL_LOW_PRIORITY | 있으면 좋으나 후순위 (**정상 결과**) |
| RECOMMENDED | 부족 역량 보완 수단으로 추천 |
| REQUIRED_OR_STRONGLY_PREFERRED | 공고/직무상 강하게 필요 |
| NOT_FEASIBLE_FOR_THIS_APPLICATION | 이번 지원엔 일정상 어려움, 장기 보완용 |

"자격증을 추천하지 않는 것도 전략" — NOT_NEEDED/OPTIONAL_LOW_PRIORITY 를 정상 취급. 예시: "현재 공고엔
자격증보다 Spring Boot 프로젝트·배포 경험 보완이 더 중요. SQLD·정보처리기사는 보조 어필이나 현 시점 후순위."

## 6. 최소 자격증 카탈로그 (완전한 일정 DB 아님)

```text
CertificateCatalogItem { name, org, officialUrl, relatedSkills[], recommendConditions, scheduleLookupMethod }
```
예: 정보처리기사·SQLD·ADsP·리눅스마스터·AWS SAA·네트워크관리사. 일정은 카탈로그에 박지 않고 필요 시 조회.
카탈로그는 **AI 임의 생성 차단**(자격증명 whitelist)용이지 일정 소스가 아니다.

## 7. 서버 구조 (제안)

```text
C 판단(A 프로필 vs B 공고/기업) → "자격증/학습/기업정보 필요?" 신호
→ CertificateNeedGate (OFF 기본, 5조건)
   └ ON 인 후보만 → CertificateResearchService (공유 조회 인터페이스, 초기 stub)
       └ EvidenceItem(status/freshness/source) 반환
→ C 는 EvidenceItem·상태값 안에서만 일정/최신정보 서술 (프롬프트엔 게이트 ON 시에만 주입)
```
판단값(fitScore/applyDecision 등)은 규칙엔진 소유 유지(뉴로-심볼릭 불변식). 근거 조회는 설명·전략 텍스트에만 영향.

## 8. 완료 기준

일정/솔직함:
- 공식 일정 미확인 시 날짜를 만들지 않는다.
- STALE/CONFLICTING/NOT_FOUND 를 상태값으로 정직히 노출.
- 일정 조회는 cert-need-gate ON 후보에 대해서만.

자격증 보조성:
- 자격증 필요 신호 없는 공고에선 추천이 생성되지 않는다.
- 약한 관련이면 OPTIONAL_LOW_PRIORITY/NOT_NEEDED 로 내려간다.
- 자격증보다 프로젝트/학습/경험이 더 중요하면 그 방향 우선.

## 9. 이번 PR 밖 (다음 단계)

- `EvidenceItem`/`EvidenceStatus`/`Freshness` 공용 도출(B 패턴 미러), `CertificateNeedGate`,
  `CertificateResearchService`(stub), 최소 카탈로그, 상태값 기반 프롬프트 주입 규칙, 답변 템플릿 구현.
- 기업맥락 conflation 실측(OSS 복귀 시): OSS 에 기업맥락 유/무 × gate 픽스처 → 판정단 conflation 델타.
  늘면 기업맥락을 외부 provider 한정으로 제한. (이번 PR 은 기업맥락 연동 + 불변식/skew 가드 테스트까지.)

## 10. 구현 진행 + 결정 로그 (실측·검토 반영)

**진행:** Increment 1 게이트(06ff8636) · 2a 국가기술자격 시험일정 provider(f2f35b14) · 2b 민간 등록정보 provider + 상태모델 정정.

**공공데이터 실측(직접 curl, 문서 아님):**
- 민간 odcloud(`api.odcloud.kr/api/15075600/v1/uddi:fadae7c0-...`): 안정·JSON, `cond[자격명::LIKE]` 서버측 필터 작동(예: '데이터분석' matchCount 27, 현재상태 등록완료/등록폐지 혼재) → 82K 벌크 불필요, on-demand 이름조회.
- 국가 q-net(목록 15003024 / 일정 15003029): 간헐 불안정 — 정상 envelope 안에 resultCode 99(SocketTimeout). 인증은 유효해 보이나 **resultCode=00 정상 응답 전까지 키 검증 완료로 닫지 않음**(회복 시 재검증).

**상태모델 정정(장애≠부재, ChatGPT 지적 수용·독립 검증됨):**
- ScheduleEvidenceStatus 에 `UPSTREAM_UNAVAILABLE`(API 오류/타임아웃/resultCode≠00) + `MANUAL_REQUIRED`(민간 일정=기관/수동) 추가.
- q-net code-99 는 `NOT_FOUND`(일정 없음)가 아니라 `UPSTREAM_UNAVAILABLE`. C 문구: "일정이 없습니다" ✗ → "공식 API 가 일시적으로 응답하지 않아 확인하지 못했습니다. 날짜를 임의 추정하지 않고 재조회/Q-Net 확인 필요" ○.
- odcloud 는 안정적이라 매칭 0건=실제 미등록(NOT_FOUND), API 오류만 UPSTREAM_UNAVAILABLE 로 구분.

**키 배포 결정(냉정한 판단):** serviceKey 는 **repo 커밋하지 않고 env 로만**(`CAREERTUNER_CERT_DATA_GO_KR_SERVICE_KEY`). 근거: (1) CI/CD 는 GitHub Secrets + EC2 호스트 env 로 이미 다른 키(OPENAI 등)를 주입하는 구조라 동일 패턴이면 됨, (2) provider 가 키 없으면 `enabled()=false` 로 라이브 호출을 아예 안 해 **앱/배포가 깨지지 않음**(사장님의 "모두 깨짐" 우려는 이미 해소). 저스테이크 공개데이터 키라 커밋해도 치명적이진 않으나, GitHub 시크릿스캔·키 남용·나쁜 선례를 피하려 Secrets/호스트 env 권장. 최종 배치는 사장님 선택(스캐폴딩은 준비됨).

**로컬 캐시/오프라인 백업(사장님 아이디어 수용):** 이 데이터는 비실시간(민간 스냅샷 20251231, 국가 목록·일정은 연초 확정, 수일~반기 갱신). → 국가자격 목록(15003024, ~1천건 소형)은 q-net 회복 시 1회 가져와 **번들 리소스 캐시**(스냅샷 라벨 표기) + 라이브는 갱신/보완용. 민간은 on-demand LIKE 조회로 충분(대용량 CSV 를 main repo 에 커밋하지 않음 — AGENTS 규칙). 캐시 사용 시 freshness 를 항상 표기.

**인터넷 팩트체크 범위(줏대 있는 판단):** 사장님이 "로컬이면 인터넷으로 팩트체크"를 제안했으나, **일반 웹검색 팩트체크 계층을 C 가 새로 만들지 않는다.** 공식 API(odcloud/q-net)가 곧 팩트체크다. 추가 웹조회가 필요하면 B 의 기존 인프라(`CompanyWebSearchClient`/`CompanyEvidenceCollector`)를 재사용한다(C 자체 웹파이프라인 금지 원칙). 민간 일정처럼 중앙 데이터가 없으면 웹 스크레이핑이 아니라 `MANUAL_REQUIRED`(주관기관 공식 페이지 확인 유도)로 솔직히 degrade.

**자격증 추천 탭 = 명시적 호출(사장님 지적 반영):** 프론트 `/applications?tab=learning`(LearningRecommendationPanel) 열람은 사용자의 명시적 자격증 요청 → cert-need-gate 의 `USER_REQUESTED`(현재 mock 에 false 하드코딩)를 이 경로에서 true 로 흘려 게이트 ON. 이때 공고상 효용이 낮아도 자격증을 말하되 status 를 OPTIONAL_LOW_PRIORITY/NOT_NEEDED 로 낮추고 **효용성을 솔직히** 설명. (백엔드 플래그 배선 + 탭 트리거는 후속 슬라이스.)

**장기·직군 전략 모드(사장님 지적 반영):** 자격증 추천은 특정 공고뿐 아니라 사용자 전체 취업희망(desiredJob) 기준의 **장기 전략**도 제공해야 함("이 공고와 무관해도 이 자격증을 따두면 좋다"). 단기 공고 비교분석과 별개의 career-wide 모드로 확장(후속 슬라이스). 장기전략은 본래 구현 요소.
