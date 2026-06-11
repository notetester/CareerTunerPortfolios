import { useEffect, useCallback, type ReactNode } from "react";
import { X } from "lucide-react";
import "./confirm-dialog.css";

export type DialogVariant = "info" | "warning" | "danger" | "success";

export interface DialogMeta {
  label: string;
  value: string;
}

export interface ConfirmDialogProps {
  variant?: DialogVariant;
  icon?: ReactNode;
  title: string;
  description: string;
  meta?: DialogMeta[];
  confirmLabel: string;
  cancelLabel?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  variant = "info",
  icon,
  title,
  description,
  meta,
  confirmLabel,
  cancelLabel = "취소",
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const handleKey = useCallback(
    (e: KeyboardEvent) => { if (e.key === "Escape") onCancel(); },
    [onCancel],
  );

  useEffect(() => {
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [handleKey]);

  const btnCls = variant === "danger" ? "av-btn av-btn--red" : "av-btn av-btn--ink";

  return (
    <div className={`dv dv--${variant}`} role="alertdialog" aria-modal aria-label={title}>
      <div className="dv__ov" onClick={onCancel} />
      <div className="dv__card">
        <button className="dv__x" onClick={onCancel} aria-label="닫기"><X /></button>
        <div className="dv__h">
          {icon}
          <span className="dv__t">{title}</span>
        </div>
        <div className="dv__d">{description}</div>
        {meta && meta.length > 0 && (
          <div className="dv__meta">
            {meta.map((m) => (
              <div className="dv__mrow" key={m.label}>
                <span className="k">{m.label}</span>
                <span className="v num">{m.value}</span>
              </div>
            ))}
          </div>
        )}
        <div className="dv__f">
          <button className="av-btn" onClick={onCancel}>{cancelLabel}</button>
          <button className={btnCls} onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}
