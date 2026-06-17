import { useLocation } from "react-router";
import { AlertTriangle, CreditCard, FileClock, ListTree } from "lucide-react";
import AdminShell from "../components/AdminShell";

const META: Record<string, { active: string; title: string; desc: string; icon: typeof CreditCard; note: string }> = {
  "/admin/payments": {
    active: "payments",
    title: "결제 관리",
    desc: "결제/환불/크레딧 충전 운영 화면",
    icon: CreditCard,
    note: "결제·크레딧 백엔드 API가 아직 구현되지 않아 운영 조치는 비활성화되어 있습니다.",
  },
  "/admin/plans": {
    active: "plans",
    title: "요금제 관리",
    desc: "요금제와 크레딧 정책 관리 화면",
    icon: ListTree,
    note: "요금제 정책 저장 API가 아직 구현되지 않아 화면을 준비 중 상태로 둡니다.",
  },
  "/admin/logs": {
    active: "logs",
    title: "시스템 로그",
    desc: "운영 로그 조회 화면",
    icon: FileClock,
    note: "시스템 로그 수집/조회 API가 아직 구현되지 않아 화면을 준비 중 상태로 둡니다.",
  },
};

export function AdminPlaceholderPage() {
  const location = useLocation();
  const meta = META[location.pathname] ?? META["/admin/logs"];
  const Icon = meta.icon;

  return (
    <AdminShell active={meta.active} breadcrumb={meta.title} title={meta.title} icon={Icon} desc={meta.desc}>
      <section className="rounded-lg border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">
        <div className="flex items-start gap-3">
          <AlertTriangle className="mt-0.5 size-5 shrink-0" />
          <div>
            <div className="font-bold">준비 중인 관리자 기능입니다.</div>
            <p className="mt-1 leading-6">{meta.note}</p>
          </div>
        </div>
      </section>
    </AdminShell>
  );
}
