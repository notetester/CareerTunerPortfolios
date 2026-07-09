import { useCallback, useEffect, useState } from "react";
import { subscribeCreditBalanceChanged } from "@/app/lib/creditBalanceEvents";
import { previewAiCharge, type AiChargePreview } from "../api/aiChargePreviewApi";

const pendingPreviews = new Map<string, Promise<AiChargePreview>>();

function loadPreview(featureType: string): Promise<AiChargePreview> {
  const pending = pendingPreviews.get(featureType);
  if (pending) return pending;

  const request = previewAiCharge(featureType)
    .finally(() => pendingPreviews.delete(featureType));
  pendingPreviews.set(featureType, request);
  return request;
}

export interface AiChargePreviewState {
  preview: AiChargePreview | null;
  loading: boolean;
  error: boolean;
  refresh: () => void;
}

export function useAiChargePreview(featureType: string, enabled = true): AiChargePreviewState {
  const [preview, setPreview] = useState<AiChargePreview | null>(null);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState(false);
  const [revision, setRevision] = useState(0);

  const refresh = useCallback(() => setRevision((current) => current + 1), []);

  useEffect(() => subscribeCreditBalanceChanged(refresh), [refresh]);

  useEffect(() => {
    if (!enabled) {
      setPreview(null);
      setLoading(false);
      setError(false);
      return;
    }

    let active = true;
    setLoading(true);
    setError(false);
    void loadPreview(featureType)
      .then((result) => {
        if (active) setPreview(result);
      })
      .catch(() => {
        if (active) {
          setPreview(null);
          setError(true);
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [enabled, featureType, revision]);

  return { preview, loading, error, refresh };
}
