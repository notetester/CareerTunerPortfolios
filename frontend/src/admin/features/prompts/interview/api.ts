import { api } from "@/app/lib/api";
import type { AdminPromptView } from "./types";

/** 면접 도메인 프롬프트 9종 조회 (GET /admin/prompts/interview). */
export function getInterviewPromptViews(): Promise<AdminPromptView[]> {
  return api<AdminPromptView[]>("/admin/prompts/interview", { method: "GET" });
}
