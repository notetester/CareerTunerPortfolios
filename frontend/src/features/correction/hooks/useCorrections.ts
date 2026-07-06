import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "@/app/lib/api";
import {
  notifyAndAcknowledgeAiCharge,
  toastAiChargeCompleted,
} from "@/features/billing/api/aiChargePreviewApi";
import { createCorrection, getCorrection, listCorrections } from "../api/correctionApi";
import type {
  CorrectionResponse,
  CorrectionSubmitRequest,
  CorrectionType,
} from "../types/correction";

const HISTORY_LIMIT = 20;

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

  const submit = useCallback(async (request: CorrectionSubmitRequest) => {
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
      const result = await createCorrection({
        ...request,
        policyAcknowledgementKey: acknowledged.policyAcknowledgementKey,
      });
      toastAiChargeCompleted(acknowledged.preview, result);
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

  return {
    history,
    selected,
    historyLoading,
    historyError,
    detailLoadingId,
    submitting,
    submitError,
    loadHistory,
    selectHistory,
    submit,
  };
}
