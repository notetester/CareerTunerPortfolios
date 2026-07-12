import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/app/lib/api";
import {
  notifyAndAcknowledgeAiCharge,
  toastAiChargeCompleted,
} from "@/features/billing/api/aiChargePreviewApi";
import type { AiModelChoice } from "@/app/components/ai/ModelPicker";
import { createCorrection, deleteCorrection, getCorrection, listCorrections } from "../api/correctionApi";
import type {
  CorrectionResponse,
  CorrectionSubmitRequest,
  CorrectionType,
} from "../types/correction";

const HISTORY_LIMIT = 20;
const PENDING_REQUEST_PREFIX = "careertuner:correction:pending:";

function errorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError || error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}

export function useCorrections(correctionType: CorrectionType, applicationCaseId: number | null) {
  const [history, setHistory] = useState<CorrectionResponse[]>([]);
  const [selected, setSelected] = useState<CorrectionResponse | null>(null);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [detailLoadingId, setDetailLoadingId] = useState<number | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const historyRequestId = useRef(0);
  const detailRequestId = useRef(0);
  const submitRequestId = useRef(0);
  const submittingRef = useRef(false);
  const scopeKey = `${correctionType}:${applicationCaseId ?? "all"}`;
  const scopeKeyRef = useRef(scopeKey);
  scopeKeyRef.current = scopeKey;

  const loadHistory = useCallback(async () => {
    const requestId = ++historyRequestId.current;
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      const rows = await listCorrections({
        correctionType,
        applicationCaseId: applicationCaseId ?? undefined,
        limit: HISTORY_LIMIT,
      });
      if (requestId === historyRequestId.current) {
        setHistory(rows);
      }
    } catch (error) {
      if (requestId === historyRequestId.current) {
        setHistory([]);
        setHistoryError(errorMessage(error, "최근 첨삭 기록을 불러오지 못했습니다."));
      }
    } finally {
      if (requestId === historyRequestId.current) {
        setHistoryLoading(false);
      }
    }
  }, [applicationCaseId, correctionType]);

  useEffect(() => {
    detailRequestId.current += 1;
    setDetailLoadingId(null);
    setSelected(null);
    setSubmitError(null);
    void loadHistory();
  }, [loadHistory]);

  const submit = useCallback(async (request: CorrectionSubmitRequest, model: AiModelChoice = "AUTO") => {
    if (submittingRef.current) return null;
    const requestId = ++submitRequestId.current;
    const requestScope = scopeKeyRef.current;
    submittingRef.current = true;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const acknowledged = await notifyAndAcknowledgeAiCharge(
        `CORRECTION_${request.correctionType}`,
      );
      const requestKey = await pendingRequestKey(requestScope, request);
      const result = await createCorrection({
        ...request,
        policyAcknowledgementKey: acknowledged.policyAcknowledgementKey,
        requestKey,
      }, model);
      clearPendingRequest(requestScope, requestKey);
      if (!result.replayed) {
        toastAiChargeCompleted(acknowledged.preview, result);
      }
      if (requestId === submitRequestId.current && requestScope === scopeKeyRef.current) {
        setSelected(result);
        setHistory((current) => [result, ...current.filter((item) => item.id !== result.id)].slice(0, HISTORY_LIMIT));
      }
      return result;
    } catch (error) {
      if (requestId === submitRequestId.current && requestScope === scopeKeyRef.current) {
        setSubmitError(errorMessage(error, "첨삭을 완료하지 못했습니다. 잠시 후 다시 시도해 주세요."));
      }
      return null;
    } finally {
      if (requestId === submitRequestId.current) {
        submittingRef.current = false;
        setSubmitting(false);
      }
    }
  }, []);

  const selectHistory = useCallback(async (id: number) => {
    const requestId = ++detailRequestId.current;
    setDetailLoadingId(id);
    setSubmitError(null);
    try {
      const result = await getCorrection(id);
      if (requestId === detailRequestId.current) {
        setSelected(result);
      }
    } catch (error) {
      if (requestId === detailRequestId.current) {
        setSubmitError(errorMessage(error, "첨삭 상세를 불러오지 못했습니다."));
      }
    } finally {
      if (requestId === detailRequestId.current) {
        setDetailLoadingId(null);
      }
    }
  }, []);

  const remove = useCallback(async (id: number) => {
    if (deletingId !== null) return false;
    setDeletingId(id);
    setSubmitError(null);
    try {
      await deleteCorrection(id);
      setHistory((current) => current.filter((item) => item.id !== id));
      setSelected((current) => current?.id === id ? null : current);
      return true;
    } catch (error) {
      setSubmitError(errorMessage(error, "첨삭 기록을 삭제하지 못했습니다."));
      return false;
    } finally {
      setDeletingId(null);
    }
  }, [deletingId]);

  return {
    history,
    selected,
    historyLoading,
    historyError,
    detailLoadingId,
    deletingId,
    submitting,
    submitError,
    loadHistory,
    selectHistory,
    remove,
    submit,
  };
}

interface PendingCorrectionRequest {
  fingerprint: string;
  requestKey: string;
}

async function pendingRequestKey(scopeKey: string, request: CorrectionSubmitRequest) {
  const storageKey = `${PENDING_REQUEST_PREFIX}${scopeKey}`;
  const fingerprint = await requestFingerprint(request);
  try {
    const stored = sessionStorage.getItem(storageKey);
    if (stored) {
      const pending = JSON.parse(stored) as PendingCorrectionRequest;
      if (pending.fingerprint === fingerprint && pending.requestKey) {
        return pending.requestKey;
      }
    }
  } catch {
    // 저장소가 차단된 환경에서도 현재 요청의 서버 멱등성은 유지한다.
  }

  const requestKey = createRequestKey();
  try {
    sessionStorage.setItem(storageKey, JSON.stringify({ fingerprint, requestKey }));
  } catch {
    // 저장소가 차단되면 현재 페이지 수명 동안의 중복 제출 방지만 적용된다.
  }
  return requestKey;
}

function clearPendingRequest(scopeKey: string, requestKey: string) {
  const storageKey = `${PENDING_REQUEST_PREFIX}${scopeKey}`;
  try {
    const stored = sessionStorage.getItem(storageKey);
    if (!stored) return;
    const pending = JSON.parse(stored) as PendingCorrectionRequest;
    if (pending.requestKey === requestKey) sessionStorage.removeItem(storageKey);
  } catch {
    try {
      sessionStorage.removeItem(storageKey);
    } catch {
      // 저장소가 차단된 환경에서는 정리할 항목도 유지할 수 없다.
    }
  }
}

function createRequestKey() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `correction:${crypto.randomUUID()}`;
  }
  return `correction:${Date.now()}:${Math.random().toString(36).slice(2)}`;
}

async function requestFingerprint(request: CorrectionSubmitRequest) {
  const value = JSON.stringify(request);
  if (typeof crypto !== "undefined" && crypto.subtle) {
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
    return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
  }

  let hash = 2166136261;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return `fallback:${(hash >>> 0).toString(16)}`;
}
