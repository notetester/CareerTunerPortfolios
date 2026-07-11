import type { AutoPrepRequest, PrepPlan } from "../types/autoPrep";
import type { PartState } from "../hooks/useAutoPrepRun";

/**
 * AI 오케스트레이터 세션 영속(localStorage) — 홈 '준비 시작' 실행 이력.
 *
 * 챗봇(F)의 서버 대화 세션과 별개다: 챗봇 분류기는 오케스트레이터로 "보내는" 입구이고,
 * 여기는 그 목적지(오케스트레이터 자체)의 실행 기록이라 저장소를 공유하지 않는다.
 * 서버 영속으로 승격할 때는 이 모듈의 CRUD 만 교체하면 된다(화면은 이 계약만 본다).
 */

/** 대화 말풍선 스냅샷 — 칩 payload 는 화면 쪽 타입(ChipData)을 그대로 직렬화한다(JSON 호환). */
export interface PrepMsg {
  role: "me" | "ai";
  text: string;
  chips?: unknown;
}

export type PrepSessionStatus = "running" | "done" | "error";

export interface PrepSessionRecord {
  id: string;
  createdAt: number;
  updatedAt: number;
  /** 사이드바 제목 — 첫 발화(또는 "첨부한 파일로 준비해줘"). */
  title: string;
  status: PrepSessionStatus;
  messages: PrepMsg[];
  /** 마지막 실행 요청 — "다시 실행"이 이걸 그대로 재실행한다. */
  lastRequest: AutoPrepRequest | null;
  /** 실행 스냅샷 — 복원 렌더용. */
  parts: PartState[];
  planSlots: PrepPlan["slots"] | null;
  caseId: number | null;
}

const KEY = "careertuner.autoprep.sessions.v1";
const MAX_RECORDS = 30;

function readAll(): PrepSessionRecord[] {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return [];
    const list = JSON.parse(raw) as PrepSessionRecord[];
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

function writeAll(list: PrepSessionRecord[]): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(list.slice(0, MAX_RECORDS)));
  } catch {
    /* 저장 실패(용량 등)는 치명 아님 — 이력만 잃는다 */
  }
}

export function listPrepSessions(): PrepSessionRecord[] {
  return readAll().sort((a, b) => b.updatedAt - a.updatedAt);
}

export function getPrepSession(id: string): PrepSessionRecord | null {
  return readAll().find((r) => r.id === id) ?? null;
}

export function createPrepSession(title: string, request: AutoPrepRequest): PrepSessionRecord {
  const now = Date.now();
  const record: PrepSessionRecord = {
    id: `prep-${now}-${Math.random().toString(36).slice(2, 8)}`,
    createdAt: now,
    updatedAt: now,
    title: title.slice(0, 80),
    status: "running",
    messages: [],
    lastRequest: request,
    parts: [],
    planSlots: null,
    caseId: null,
  };
  writeAll([record, ...readAll()]);
  return record;
}

export function updatePrepSession(id: string, patch: Partial<Omit<PrepSessionRecord, "id" | "createdAt">>): void {
  const list = readAll();
  const idx = list.findIndex((r) => r.id === id);
  if (idx < 0) return;
  list[idx] = { ...list[idx], ...patch, updatedAt: Date.now() };
  writeAll(list);
}

export function deletePrepSession(id: string): void {
  writeAll(readAll().filter((r) => r.id !== id));
}

/** epoch millis → "방금"/"n분 전"/"n시간 전"/"n일 전". */
export function prepRelativeTime(ts: number): string {
  const m = Math.floor((Date.now() - ts) / 60000);
  if (m < 1) return "방금";
  if (m < 60) return `${m}분 전`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}시간 전`;
  return `${Math.floor(h / 24)}일 전`;
}
