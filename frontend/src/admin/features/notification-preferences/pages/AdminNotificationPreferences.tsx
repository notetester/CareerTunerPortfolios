import { useEffect, useState } from "react";
import { MailCheck, BellOff } from "lucide-react";
import AdminShell from "@/admin/components/AdminShell";
import * as adminNotificationPreferenceApi from "../api";
import {
  ADMIN_NOTIFICATION_CATEGORY_METAS,
  type AdminNotificationCategories,
} from "../types";

/**
 * 관리자 알림 수신 설정(개인 opt-out) 페이지.
 *
 * 사용자용 NotificationSettings 와 별개의 관리자 전용 카드 — 관리자 알림(NEW_REPORT 등)은
 * 사용자 카테고리 토글에 노출되지 않으므로 여기서만 제어한다. 끄면 "본인만" 해당 유형의
 * 관리자 팬아웃 알림에서 제외되며, 신고/문의 큐 화면과 뱃지 카운트에는 영향이 없다.
 */
export default function AdminNotificationPreferences() {
  const [categories, setCategories] = useState<AdminNotificationCategories | null>(null);
  const [savingType, setSavingType] = useState<string | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  useEffect(() => {
    adminNotificationPreferenceApi
      .getAdminNotificationCategories()
      .then(setCategories)
      .catch(() => setToast("수신 설정을 불러오지 못했습니다."));
  }, []);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2200);
    return () => clearTimeout(t);
  }, [toast]);

  const handleToggle = async (type: string) => {
    if (!categories || savingType) return;
    const next = !(categories[type] ?? true);
    setSavingType(type);
    try {
      const updated = await adminNotificationPreferenceApi.updateAdminNotificationCategory(type, next);
      setCategories(updated);
      setToast(next ? "해당 알림을 다시 수신합니다." : "해당 알림을 더 이상 받지 않습니다.");
    } catch (e) {
      setToast(e instanceof Error && e.message ? e.message : "설정 저장에 실패했습니다.");
    } finally {
      setSavingType(null);
    }
  };

  return (
    <AdminShell
      active="admin-notification-settings"
      breadcrumb="콘텐츠/고객지원"
      title="관리자 알림 설정"
      icon={MailCheck}
      desc="관리자 업무 알림(신고·문의·가입·기업/공고 검수)의 본인 수신 여부를 설정합니다."
    >
      <section className="rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <div className="flex items-center gap-2 border-b border-slate-200 px-5 py-4 dark:border-slate-700">
          <BellOff className="size-4 text-slate-500" />
          <div>
            <h2 className="text-sm font-semibold">관리자 업무 알림 수신</h2>
            <p className="text-xs text-slate-500">
              끄면 <b>내 계정만</b> 해당 유형의 관리자 알림에서 제외됩니다. 담당 권한이 없는 유형은 켜져 있어도 발송되지 않아요.
            </p>
          </div>
        </div>

        <ul className="divide-y divide-slate-100 dark:divide-slate-800">
          {ADMIN_NOTIFICATION_CATEGORY_METAS.map((meta) => {
            const enabled = categories ? (categories[meta.type] ?? true) : true;
            const saving = savingType === meta.type;
            return (
              <li key={meta.type} className="flex items-center justify-between gap-4 px-5 py-4">
                <div>
                  <div className="text-sm font-medium">{meta.label}</div>
                  <div className="mt-0.5 text-xs text-slate-500">{meta.desc}</div>
                </div>
                <button
                  type="button"
                  role="switch"
                  aria-checked={enabled}
                  aria-label={`${meta.label} 수신 ${enabled ? "끄기" : "켜기"}`}
                  disabled={!categories || saving}
                  onClick={() => handleToggle(meta.type)}
                  className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors disabled:opacity-50 ${
                    enabled ? "bg-emerald-500" : "bg-slate-300 dark:bg-slate-600"
                  }`}
                >
                  <span
                    className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${
                      enabled ? "translate-x-5" : "translate-x-0.5"
                    }`}
                  />
                </button>
              </li>
            );
          })}
        </ul>
      </section>

      {toast && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 rounded-md bg-slate-900 px-4 py-2 text-sm text-white shadow-lg dark:bg-slate-100 dark:text-slate-900">
          {toast}
        </div>
      )}
    </AdminShell>
  );
}
