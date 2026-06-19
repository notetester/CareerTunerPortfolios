import { api } from "@/app/lib/api";

export type KnowledgeKind = "RUBRIC" | "QUESTION_BANK" | "COMPANY" | "GENERAL";

export interface InterviewKnowledge {
  id: number;
  kind: KnowledgeKind;
  title: string | null;
  content: string;
  source: string | null;
  indexed: boolean | null;
  createdAt: string;
}

export interface AddKnowledgeRequest {
  kind: KnowledgeKind;
  title?: string;
  content: string;
  source?: string;
}

export const listKnowledge = (limit = 100) =>
  api<InterviewKnowledge[]>(`/admin/interview/knowledge?limit=${limit}`, { method: "GET" });

export const addKnowledge = (req: AddKnowledgeRequest) =>
  api<InterviewKnowledge>("/admin/interview/knowledge", { method: "POST", body: JSON.stringify(req) });

export const reindexKnowledge = () =>
  api<{ reindexed: number }>("/admin/interview/knowledge/reindex", { method: "POST" });

export const updateKnowledge = (id: number, req: AddKnowledgeRequest) =>
  api<InterviewKnowledge>(`/admin/interview/knowledge/${id}`, { method: "PUT", body: JSON.stringify(req) });

export const deleteKnowledge = (id: number) =>
  api<void>(`/admin/interview/knowledge/${id}`, { method: "DELETE" });
