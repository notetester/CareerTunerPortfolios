import { Link } from "react-router";
import {
  Activity,
  BarChart3,
  CreditCard,
  FileCheck2,
  LayoutGrid,
  Scale,
  ShieldCheck,
  Users,
  type LucideIcon,
} from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { useAdminAuthorization } from "../../../auth/useAdminAuthorization";
import type { AdminPermissionCode } from "../../../auth/adminAccess";

interface PortalDestination {
  label: string;
  description: string;
  href: string;
  icon: LucideIcon;
  permissions: readonly AdminPermissionCode[];
}

const DESTINATIONS: readonly PortalDestination[] = [
  { label: "운영 종합 대시보드", description: "회원·지원·면접·AI 운영 현황을 한눈에 확인합니다.", href: "/admin/dashboard", icon: Activity, permissions: ["USER_READ", "AI_READ"] },
  { label: "회원·지원 건 운영", description: "회원과 지원 건, 기업 신청 상태를 관리합니다.", href: "/admin/users", icon: Users, permissions: ["USER_READ"] },
  { label: "AI·분석 운영", description: "분석 품질과 실행 이력, 프롬프트 운영 상태를 점검합니다.", href: "/admin/analytics", icon: BarChart3, permissions: ["AI_READ"] },
  { label: "보안 운영 센터", description: "로그인 위험, 차단 정책과 보안 검토 대기 건을 확인합니다.", href: "/admin/security", icon: ShieldCheck, permissions: ["SECURITY_READ"] },
  { label: "결제·구독 운영", description: "결제 내역, 크레딧과 요금제 정책을 관리합니다.", href: "/admin/payments", icon: CreditCard, permissions: ["BILLING_READ"] },
  { label: "콘텐츠·고객 지원", description: "신고, 공지, 문의와 발송 알림을 처리합니다.", href: "/admin/community", icon: FileCheck2, permissions: ["CONTENT_READ"] },
  { label: "정책 관리", description: "서비스 운영 정책과 약관, 런타임 정책을 확인합니다.", href: "/admin/policies", icon: Scale, permissions: ["POLICY_READ"] },
  { label: "감사 로그", description: "보안·회원·관리자 활동의 감사 기록을 조회합니다.", href: "/admin/audit/security", icon: LayoutGrid, permissions: ["AUDIT_READ"] },
];

export function AdminPortalPage() {
  const authorization = useAdminAuthorization();
  const visibleDestinations = DESTINATIONS.filter((item) => authorization.can(...item.permissions));

  return (
    <AdminShell
      active="admin-portal"
      breadcrumb="관리자 포털"
      title="관리자 포털"
      icon={LayoutGrid}
      desc="내 권한으로 사용할 수 있는 운영 영역을 선택하세요. 각 항목은 독립 관리 화면으로 연결됩니다."
    >
      {authorization.status === "idle" || authorization.status === "loading" ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 6 }).map((_, index) => <div key={index} className="h-36 animate-pulse rounded-xl bg-slate-200" />)}
        </div>
      ) : (
        <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="접근 가능한 관리자 기능">
          {visibleDestinations.map((item) => (
            <Link key={item.href} to={item.href} className="group rounded-xl border border-slate-200 bg-card p-5 transition-colors hover:border-blue-300 hover:bg-blue-50">
              <div className="flex size-11 items-center justify-center rounded-xl bg-blue-50 text-blue-700 group-hover:bg-card">
                <item.icon className="size-5" />
              </div>
              <h2 className="mt-4 font-black text-slate-900">{item.label}</h2>
              <p className="mt-2 text-sm leading-6 text-slate-500">{item.description}</p>
            </Link>
          ))}
          {visibleDestinations.length === 0 && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-800 sm:col-span-2 xl:col-span-3">
              현재 계정에 조회 가능한 관리자 영역이 없습니다. 최고 관리자에게 세부 권한을 요청해 주세요.
            </div>
          )}
        </section>
      )}
    </AdminShell>
  );
}
