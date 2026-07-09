const AI_FEATURE_LABELS: Record<string, string> = {
  APPLICATION_ANALYSIS: "공고·지원 건 분석",
  APPLICATION_CASE_PIPELINE: "지원 건 자동 분석",
  JOB_POSTING_METADATA: "공고 정보 추출",
  JOB_POSTING_OCR: "공고 이미지·문서 인식",
  JOB_ANALYSIS: "공고 분석",
  JOB_REQUIRED_CONDITION: "필수 조건 분석",
  JOB_PREFERRED_CONDITION: "우대 조건 분석",
  JOB_DUTY_SUMMARY: "직무 내용 요약",
  COMPANY_ANALYSIS: "기업 분석",
  COMPANY_RESEARCH: "기업 분석",
  FIT_ANALYSIS: "적합도 분석",
  INTERVIEW_POINT_EXTRACTION: "면접 포인트 추출",

  INTERVIEW_QUESTION: "예상 질문 생성",
  INTERVIEW_QUESTION_GEN: "예상 질문 생성",
  INTERVIEW_FOLLOWUP_GEN: "후속 질문 생성",
  INTERVIEW_MODEL_ANSWER: "모범 답변 생성",
  INTERVIEW_ANSWER_EVAL: "면접 답변 평가",
  INTERVIEW_CRITIC: "면접 답변 보완 분석",
  INTERVIEW_PLANNER: "면접 진행 계획",
  INTERVIEW_DIALOGUE: "AI 모의면접",
  INTERVIEW_REPORT: "면접 리포트",
  INTERVIEW_VOICE_SESSION: "음성 AI 면접",
  INTERVIEW_VOICE_SCORING: "음성 답변 평가",
  INTERVIEW_VIDEO_ANALYSIS: "영상 면접 분석",
  INTERVIEW_AVATAR_SESSION: "AI 아바타 면접",

  CORRECTION_INTERVIEW_ANSWER: "면접 답변 첨삭",
  CORRECTION_SELF_INTRO: "자기소개서 첨삭",
  CORRECTION_RESUME: "이력서 첨삭",
  CORRECTION_PORTFOLIO: "포트폴리오 첨삭",

  CAREER_TREND: "장기 취업 분석",
  CAREER_TREND_ANALYSIS: "취업 경향 분석",
  LONG_TERM_ANALYSIS: "장기 취업 분석",
  GAP_ROADMAP_RECOMMENDATION: "역량 보완 로드맵 추천",
  CERTIFICATION_RECOMMENDATION: "자격증 추천",
  NEXT_APPLICATION_RECOMMENDATION: "다음 지원 추천",
  USAGE_PLAN_RECOMMENDATION: "AI 활용 계획 추천",
  DASHBOARD_SUMMARY: "대시보드 요약",
  DASHBOARD_INSIGHT: "대시보드 맞춤 인사이트",

  PROFILE_SUMMARY: "프로필 요약",
  PROFILE_SKILL_EXTRACT: "보유 기술 추출",
  PROFILE_SELF_INTRO_KEYWORD: "자기소개 핵심어 추출",
  PROFILE_CAREER_KEYWORD: "경력 핵심어 추출",
  PROFILE_COMPLETENESS: "프로필 완성도",

  COMMUNITY_INTERVIEW_SUMMARY: "면접 후기 요약",
  COMMUNITY_AUTO_TAGGING: "게시글 자동 태그",
  COMMUNITY_INTERVIEW_QUESTION_EXTRACTION: "면접 질문 추출",
  COMMUNITY_POST_RECOMMENDATION: "맞춤 게시글 추천",
};

const AI_FEATURE_GROUP_LABELS: Array<[prefix: string, label: string]> = [
  ["CORRECTION_", "AI 첨삭"],
  ["INTERVIEW_", "AI 면접"],
  ["PROFILE_", "프로필 AI 분석"],
  ["COMMUNITY_", "커뮤니티 AI 기능"],
  ["DASHBOARD_", "대시보드 AI 분석"],
  ["CAREER_", "취업 전략 분석"],
  ["JOB_", "공고 AI 분석"],
  ["COMPANY_", "기업 AI 분석"],
];

export function getAiFeatureLabel(featureType: string): string {
  const normalized = featureType.trim().toUpperCase();
  const label = AI_FEATURE_LABELS[normalized];
  if (label) return label;

  return AI_FEATURE_GROUP_LABELS.find(([prefix]) => normalized.startsWith(prefix))?.[1]
    ?? "기타 AI 기능";
}
