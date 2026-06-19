/** 면접 프롬프트 운영 화면용 — 백엔드 AdminPromptView(6필드)와 동일. */
export interface AdminPromptView {
  feature: string;
  name: string;
  version: string;
  purpose: string;
  systemPrompt: string;
  schemaSummary: string;
}
