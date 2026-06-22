export const meta = {
  name: 'gen-interview-dataset',
  description: '면접 6task 합성 데이터 생성 — Claude(선생)가 seed별 fan-out으로 분석+질문+채점 생성',
  phases: [
    { title: 'QGEN', detail: 'seed → 가짜 분석 + 질문6' },
    { title: 'EVAL', detail: '질문 → 모범답안 + 답변3종 채점' },
    { title: 'PROBE', detail: '압박 답변 → 반박 꼬리질문 (압박 모드만)' },
    { title: 'REPORT', detail: '전체 Q&A → 품질별 종합 리포트' },
  ],
}

// ── seed 생성 (인덱스 기반 결정론적 — Math.random 미사용) ──
const FAMILIES = {
  '개발': { titles: ['백엔드 개발자', '프론트엔드 개발자', '데이터 엔지니어', 'DevOps 엔지니어'],
           skills: ['Java', 'Spring Boot', 'MySQL', 'React', 'AWS', 'Docker', 'Redis', 'Kafka'] },
  '마케팅': { titles: ['퍼포먼스 마케터', '콘텐츠 마케터', '그로스 마케터'],
            skills: ['GA4', '메타광고', 'SQL', '퍼널분석', 'CRM', 'SEO'] },
  '생산관리': { titles: ['생산관리 담당', '품질관리(QC)', '공정 엔지니어'],
             skills: ['공정관리', '품질시스템', 'SPC', 'GMP', 'ERP', '6시그마'] },
  '서비스운영': { titles: ['CS 운영 매니저', '서비스 기획자', '물류 운영 담당'],
              skills: ['CS운영', '서비스기획', '데이터분석', 'SLA관리', 'VOC분석'] },
}
const INDUSTRIES = ['IT/SaaS', '핀테크', '이커머스', '제조', '바이오/제약', '게임', '물류', '금융']
const TYPES = ['스타트업', '중견기업', '대기업', '외국계']
const SENIORITY = ['신입', '주니어(1~3년)', '시니어(5년+)']
const MODES = ['BASIC', 'JOB', 'PERSONALITY', 'PRESSURE', 'RESUME', 'COMPANY']
const KO_P = ['넥스트', '그린', '스마트', '코어', '노바', '링크']
const KO_S = ['페이', '랩스', '소프트', '테크', '웍스', '데이터']
const famKeys = Object.keys(FAMILIES)

function makeSeeds(n, offset) {
  const out = []
  for (let i = offset; i < offset + n; i++) {
    const fk = famKeys[i % famKeys.length]
    const fam = FAMILIES[fk]
    const sk0 = (i * 2) % fam.skills.length
    let skills = fam.skills.slice(sk0, sk0 + 4)
    if (skills.length < 4) skills = fam.skills.slice(0, 4)
    out.push({
      id: `seed_${String(i + 1).padStart(3, '0')}`,
      company_name: `${KO_P[i % KO_P.length]}${KO_S[(i + 2) % KO_S.length]}`,
      industry: INDUSTRIES[i % INDUSTRIES.length],
      company_type: TYPES[i % TYPES.length],
      job_title: fam.titles[i % fam.titles.length],
      job_family: fk,
      seniority: SENIORITY[i % SENIORITY.length],
      mode: MODES[i % MODES.length],
      lang: i % 7 === 0 ? 'en' : 'ko',
      skill_hints: skills,
    })
  }
  return out
}

// seed_001~012 는 기존 학습 완료 — seed_013(offset=12)부터 38개만 신규 생성
const OFFSET = (args && args.offset != null) ? args.offset : 12
const N = (args && args.n != null) ? args.n : 38
const baseSeeds = makeSeeds(N, OFFSET)
// QGEN 데이터 보강(2026-06-20): seed 당 6모드로 펼쳐 질문 다양성·QGEN 데이터량 6배 확보
const seeds = baseSeeds.flatMap((s) => MODES.map((m) => ({ ...s, mode: m, id: `${s.id}_${m.toLowerCase()}` })))
log(`seed ${baseSeeds.length} × ${MODES.length}모드 = ${seeds.length}건 — fan-out 시작`)

const MODE_FOCUS = {
  BASIC: '자기소개·지원동기·장단점 등 기본 질문',
  JOB: '필수 스킬별 기술 질문 위주',
  PERSONALITY: '협업·갈등·책임감 + 컬처핏',
  PRESSURE: '도전적 질문 + 약점 추궁',
  RESUME: '자기소개서 문장 기반 질문',
  COMPANY: '회사 이해도·지원동기',
}

const ANALYSIS_Q_SCHEMA = {
  type: 'object', additionalProperties: false,
  required: ['company_analysis', 'job_analysis', 'questions'],
  properties: {
    company_analysis: {
      type: 'object', additionalProperties: false,
      required: ['industry', 'company_summary', 'interview_points', 'verified_facts', 'ai_inferences'],
      properties: {
        industry: { type: 'string' },
        company_summary: { type: 'string' },
        recent_issues: { type: 'string' },
        interview_points: { type: 'string' },
        competitors: { type: 'array', items: { type: 'string' } },
        verified_facts: { type: 'array', items: { type: 'object', additionalProperties: false, required: ['fact', 'source'], properties: { fact: { type: 'string' }, source: { type: 'string' } } } },
        ai_inferences: { type: 'array', items: { type: 'object', additionalProperties: false, required: ['basis', 'inference'], properties: { basis: { type: 'string' }, inference: { type: 'string' } } } },
      },
    },
    job_analysis: {
      type: 'object', additionalProperties: false,
      required: ['required_skills', 'duties', 'difficulty'],
      properties: {
        required_skills: { type: 'array', items: { type: 'string' } },
        preferred_skills: { type: 'array', items: { type: 'string' } },
        duties: { type: 'string' },
        difficulty: { type: 'string', enum: ['EASY', 'NORMAL', 'HARD'] },
        ambiguous_conditions: { type: 'array', items: { type: 'object', additionalProperties: false, required: ['condition', 'assumption'], properties: { condition: { type: 'string' }, assumption: { type: 'string' } } } },
      },
    },
    questions: {
      type: 'array',
      items: { type: 'object', additionalProperties: false, required: ['question', 'question_type'], properties: { question: { type: 'string' }, question_type: { type: 'string', enum: ['TECH', 'EXPECTED', 'PERSONALITY', 'SITUATION'] } } },
    },
  },
}

const EVAL_SCHEMA = {
  type: 'object', additionalProperties: false, required: ['items'],
  properties: {
    items: {
      type: 'array',
      items: {
        type: 'object', additionalProperties: false, required: ['question_index', 'model_answer', 'cases'],
        properties: {
          question_index: { type: 'integer' },
          model_answer: { type: 'string' },
          cases: {
            type: 'array',
            items: { type: 'object', additionalProperties: false, required: ['quality', 'answer', 'score', 'feedback'], properties: { quality: { type: 'string', enum: ['good', 'fair', 'poor'] }, answer: { type: 'string' }, score: { type: 'integer' }, feedback: { type: 'string' } } },
          },
        },
      },
    },
  },
}

const PROBE_SCHEMA = {
  type: 'object', additionalProperties: false, required: ['items'],
  properties: {
    items: {
      type: 'array',
      items: {
        type: 'object', additionalProperties: false, required: ['question_index', 'quality', 'probe'],
        properties: {
          question_index: { type: 'integer' },
          quality: { type: 'string', enum: ['good', 'fair', 'poor'] },
          probe: { type: 'string' },
        },
      },
    },
  },
}

const REPORT_SCHEMA = {
  type: 'object', additionalProperties: false, required: ['items'],
  properties: {
    items: {
      type: 'array',
      items: {
        type: 'object', additionalProperties: false,
        required: ['quality', 'total_score', 'categories', 'summary_feedback'],
        properties: {
          quality: { type: 'string', enum: ['good', 'fair', 'poor'] },
          total_score: { type: 'integer' },
          categories: {
            type: 'array',
            items: { type: 'object', additionalProperties: false, required: ['name', 'score'], properties: { name: { type: 'string' }, score: { type: 'integer' } } },
          },
          summary_feedback: { type: 'array', items: { type: 'string' } },
        },
      },
    },
  },
}

const results = await pipeline(seeds,
  (seed) => agent(
    `너는 한국 IT 취업 면접 데이터 생성기다. 아래 가상 지원 건에 대해 실제 채용 분석처럼 풍부하고 약간 장황한 가짜 분석을 만들고, 면접 질문 6개를 생성하라.

회사명: ${seed.company_name}
업종/유형: ${seed.industry} / ${seed.company_type}
직무: ${seed.job_title}
경력 수준: ${seed.seniority}
스킬 힌트: ${seed.skill_hints.join(', ')}
면접 모드: ${seed.mode} (${MODE_FOCUS[seed.mode] || ''})
언어 맥락: ${seed.lang === 'en' ? '채용공고는 영어권 외국계 맥락, 단 면접 질문은 한국어로 출력' : '한국어'}

규칙:
- company_analysis/job_analysis 는 실제 채용 분석처럼 구체적으로. verified_facts 5~8개(각 source 포함), ai_inferences 3~4개(각 basis 포함).
- 질문 6개는 위 재료를 활용하되, 난이도는 국비 초급 주니어 수준으로 의도적으로 쉽게. 과한 전문성 요구 금지.
- 면접 모드(${seed.mode})의 초점을 질문에 반영.
- 각 질문은 한국어 한 문장, question_type 은 TECH/EXPECTED/PERSONALITY/SITUATION 중 하나.`,
    { label: `qgen:${seed.id}`, phase: 'QGEN', schema: ANALYSIS_Q_SCHEMA, model: 'sonnet' }
  ),
  (gen, seed) => agent(
    `너는 한국 IT 면접 채점 데이터 생성기다. 아래 질문들 각각에 대해 (1) 모범답안 1개, (2) 답변 3종(good=우수, fair=애매, poor=망함)과 각 점수·피드백을 만들어라.

직무: ${seed.job_title} / 회사: ${seed.company_name} / 경력: ${seed.seniority}
질문 목록:
${(gen.questions || []).map((q, i) => `${i}. ${q.question}`).join('\n')}

규칙:
- model_answer: 한국어 3~5문장, 두괄식, 핵심만 간결히. 국비 주니어 수준.
- cases 3종: good(모범답안에 부합, 90~98점), fair(방향은 맞으나 빈약·짧음, 50~70점), poor(핵심 빗나감 또는 무관, 10~35점).
- 점수는 모범답안 기준 부합도로 매긴다. feedback 은 한국어 2~3문장(부족한 점·보완 방향).
- question_index 는 위 번호(0부터 시작).`,
    { label: `eval:${seed.id}`, phase: 'EVAL', schema: EVAL_SCHEMA, model: 'sonnet' }
  ).then(ev => ({ seed, analysis_q: gen, eval: ev })),
  // stage3 PROBE — 압박(PRESSURE) 모드만 반박 꼬리질문 생성. 나머지 모드는 그대로 통과(agent 호출 X).
  (prev, seed) => {
    if (seed.mode !== 'PRESSURE') return prev
    const evItems = (prev.eval && prev.eval.items) || []
    const questions = (prev.analysis_q && prev.analysis_q.questions) || []
    if (!evItems.length || !questions.length) return prev
    const block = evItems.map((it) => {
      const q = (questions[it.question_index] || {}).question || ''
      const cs = (it.cases || []).map((c) => `  [${c.quality}] ${c.answer}`).join('\n')
      return `질문${it.question_index}: ${q}\n${cs}`
    }).join('\n\n')
    return agent(
      `너는 압박 면접관이다. 아래 각 질문의 지원자 답변 3종(good/fair/poor)을 각각 반박하는 압박성 꼬리질문을 1개씩 만들어라.
약점·근거부족·모순·과장을 짚고 전제를 흔들거나 구체적 근거·수치를 요구한다. 인신공격 금지, 한국어 한 문장, 국비 주니어 수준에서 1회만 깊게 찌르고 멈춘다.

${block}

각 (질문 index × 답변 quality) 조합마다 items 에 {question_index, quality, probe} 를 하나씩 넣는다.`,
      { label: `probe:${seed.id}`, phase: 'PROBE', schema: PROBE_SCHEMA, model: 'sonnet' }
    ).then((pb) => ({ ...prev, probe: pb }))
  },
  // stage4 REPORT — 전 모드. 답변 품질(good/fair/poor) 세트별 종합 리포트 3개.
  (prev, seed) => {
    const evItems = (prev.eval && prev.eval.items) || []
    const questions = (prev.analysis_q && prev.analysis_q.questions) || []
    if (!evItems.length || !questions.length) return prev
    const block = evItems.map((it) => {
      const q = (questions[it.question_index] || {}).question || ''
      const cs = (it.cases || []).map((c) => `  [${c.quality}] (${c.score}점) ${c.answer}`).join('\n')
      return `질문${it.question_index}: ${q}\n${cs}`
    }).join('\n\n')
    return agent(
      `너는 면접 결과를 종합 평가하는 면접관이다. 아래는 한 지원 건의 질문 6개와 각 질문에 대한 답변 3종(good/fair/poor)·점수다.
답변 품질 세트별로(good만 모은 우수 면접 / fair만 모은 보통 면접 / poor만 모은 미흡 면접) 각각 종합 리포트를 1개씩, 총 3개 만들어라.
- total_score: 0~100 종합(해당 세트 답변 점수들과 정합되게).
- categories: 항목별 점수 4~6개(답변 내용/직무 적합성/구체성/논리성/표현력/태도 등).
- summary_feedback: 핵심 피드백 3개 내외 한국어 문장.

직무: ${seed.job_title} / 회사: ${seed.company_name} / 경력: ${seed.seniority}

${block}

items 에 quality(good/fair/poor)별 리포트를 하나씩 넣는다.`,
      { label: `report:${seed.id}`, phase: 'REPORT', schema: REPORT_SCHEMA, model: 'sonnet' }
    ).then((rp) => ({ ...prev, report: rp }))
  }
)

const ok = results.filter(Boolean)
log(`완료: ${ok.length}/${seeds.length} seed`)
return ok