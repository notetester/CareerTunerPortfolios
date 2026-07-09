// C_FIT_EXPLAIN 합성 데이터 distillation 워크플로우 (선생 = sonnet)
// (D의 ml/interview-finetune/generate_dataset.workflow.js 를 참고하되 C 폴더에 새로 작성. D 파일 미수정)
//
// 설계: 규칙엔진은 Python seed_profiles.py 가 단일 소스. 이 워크플로우는 점수를 만들지 않고,
//   이미 점수/판단/매칭이 계산된 '시드 파일'의 슬라이스를 배치별로 읽어 teacher 가 설명만 생성한다.
//
// 입력(args): { seedsPath: 절대경로 JSONL, n: 시드 수, batchSize: 배치당 시드 수 }
// 출력(result): { items: [{ seedId, fit_explain }] }  ← Python join_raw.py 가 seeds 와 조인해 raw.json 생성
//
// ★ 뉴로-심볼릭 보장: fit_explain 스키마에 additionalProperties:false 를 걸어
//   fitScore/score/applyDecision/decision 같은 점수·판단 키 생성을 '구조적으로' 차단한다.
//
// 실행(메인 루프에서):
//   1) python seed_profiles.py --n 300 --balance --out <seedsPath>
//   2) Workflow({ scriptPath: 이 파일, args: { seedsPath, n:300, batchSize:15 } })
//   3) python join_raw.py --seeds <seedsPath> --wf-output <temp .output> --out raw.json
//   확장: n 만 1000/3000 으로 바꾸면 됨(배치 수 자동 증가).

export const meta = {
  name: 'c-fit-explain-distill',
  description: 'C_FIT_EXPLAIN 합성 데이터 distillation (sonnet 선생, 시드 슬라이스 배치 생성)',
  phases: [{ title: 'Distill', detail: '시드 배치별 teacher(sonnet) fit_explain 생성' }],
}

// args 는 객체 또는 JSON 문자열 둘 다 올 수 있어 방어적으로 파싱한다.
const A = (typeof args === 'string') ? JSON.parse(args) : (args || {})
const seedsPath = A.seedsPath
const n = A.n
const bs = A.batchSize ? A.batchSize : 15
const numBatches = Math.ceil(n / bs)
log(`args 파싱: seedsPath=${seedsPath} n=${n} batchSize=${bs} numBatches=${numBatches}`)

// fit_explain 출력 스키마(점수·판단 키 차단). 필수 키 고정.
const FIT_EXPLAIN = {
  type: 'object',
  additionalProperties: false,
  properties: {
    fitSummary: { type: 'string' },
    strengths: { type: 'array', items: { type: 'string' } },
    risks: { type: 'array', items: { type: 'string' } },
    strategyActions: { type: 'array', items: { type: 'string' } },
    learningTaskReasons: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        properties: { skill: { type: 'string' }, why: { type: 'string' } },
        required: ['skill', 'why'],
      },
    },
  },
  required: ['fitSummary', 'strengths', 'risks', 'strategyActions', 'learningTaskReasons'],
}
const BATCH_SCHEMA = {
  type: 'object',
  properties: {
    items: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        properties: { seedId: { type: 'string' }, fit_explain: FIT_EXPLAIN },
        required: ['seedId', 'fit_explain'],
      },
    },
  },
  required: ['items'],
}

const RULES = [
  '너는 CareerTuner 의 커리어 전략 설명 모델용 학습 데이터를 만드는 선생이다.',
  '직무는 IT/개발뿐 아니라 마케팅·영업·디자인·회계/재무·인사/총무·물류/생산관리·고객상담 등 다양하다. 그 직군의 용어로 설명하고, 다른 직군(특히 IT) 전용 표현을 임의로 섞지 않는다.',
  'requiredSkills/preferredSkills/profileSkills 는 프로그래밍 기술만이 아니라 직무 요구조건·핵심 역량·도구·자격·경험 조건을 포함한다.',
  '점수(fitScore)·지원판단(applyDecision)·매칭/부족 역량은 서버 규칙엔진이 이미 계산해 시드에 들어있다.',
  '너는 점수나 판단을 새로 만들거나 바꾸지 않는다. 설명/추천 문장만 한국어로 생성한다.',
  '입력 시드에 없는 회사명·역량·도구·자격·경력·공고조건을 새로 만들지 않는다(환각 금지).',
  'matchedSkills(보유로 인정된 역량)와 missingRequiredSkills/missingPreferredSkills(부족 역량)에 모순되는 문장을 쓰지 않는다.',
  '강점(strengths)은 matchedSkills/보유 자격·경험 근거로, 위험요인(risks)은 missing 역량/조건 근거로 쓴다.',
  'learningTaskReasons 의 skill 은 missingRequiredSkills 또는 missingPreferredSkills 안의 구체 항목에서 고른다. 부족 항목이 없으면 우대 역량 중 구체 항목 하나로 하고, 추상적 표현("관련 도구" 등)은 쓰지 않는다.',
  '합격 보장·합격률 단정·차별적 표현을 쓰지 않는다.',
  'fit_explain 에 fitScore/score/applyDecision/decision 같은 점수·판단 키를 절대 넣지 않는다.',
].join('\n- ')

function batchPrompt(i) {
  const offset = i * bs + 1
  const limit = Math.min(bs, n - i * bs)
  return `- ${RULES}

작업 절차:
1) Read 도구로 시드 파일의 너의 배치 구간만 읽어라.
   path="${seedsPath}", offset=${offset}, limit=${limit}
   (각 줄이 시드 1개의 JSON. "id" 필드가 식별자다.)
2) 읽은 ${limit}개 시드 각각에 대해 fit_explain 을 생성한다.
   - fitSummary: 적합도 총평 2~3문장(주어진 점수 수준과 일치하는 톤. 점수 숫자는 본문에 굳이 반복 안 해도 됨).
   - strengths: 2~3개. matchedSkills/보유 자격증 근거.
   - risks: 1~3개. missingRequiredSkills/missingPreferredSkills 근거.
   - strategyActions: 1~3개. 지원 전 보완 액션(부족 역량을 채우는 구체 행동).
   - learningTaskReasons: 1~2개. {skill, why}. skill 은 missing 역량 중에서.
3) 한국어로 자연스럽게. 시드마다 표현을 다르게(템플릿 복붙 금지).
4) 출력: items 배열. 각 원소 { "seedId": 그 시드의 id, "fit_explain": {…} }. 점수/판단 키 금지.

이 배치는 ${limit}개 시드다. items 길이는 정확히 ${limit} 이어야 한다.`
}

const results = await parallel(
  Array.from({ length: numBatches }, (_, i) => () =>
    agent(batchPrompt(i), {
      label: `distill:b${i + 1}/${numBatches}`,
      phase: 'Distill',
      model: 'sonnet',
      schema: BATCH_SCHEMA,
    })
  )
)

const items = results.filter(Boolean).flatMap(r => (r && r.items) ? r.items : [])
log(`fit_explain 생성 ${items.length} / 목표 ${n} (배치 ${numBatches}개, 배치당 ${bs})`)
return { items, expected: n, numBatches, batchSize: bs }
