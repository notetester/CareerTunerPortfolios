import { Link, useLocation } from "react-router";
import { Badge } from "../components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Database, FileCheck2, LockKeyhole, Scale } from "lucide-react";

const legalSections = {
  "/legal/terms": { title: "이용약관", badge: "Terms", icon: Scale, desc: "회원 가입, 서비스 이용, 크레딧, 제한 행위, 책임 범위를 정의합니다." },
  "/legal/privacy": { title: "개인정보처리방침", badge: "Privacy", icon: LockKeyhole, desc: "회원 정보, 이력서, 지원 기록, 결제 정보의 수집과 보관 기준을 설명합니다." },
  "/legal/ai-data-consent": { title: "AI 데이터 이용 동의", badge: "AI Consent", icon: Database, desc: "AI 분석과 첨삭을 위해 처리되는 데이터 범위와 동의 철회 기준을 안내합니다." },
  "/legal/copyright": { title: "저작권 정책", badge: "Copyright", icon: FileCheck2, desc: "사용자가 업로드한 문서, AI 생성 결과, 커뮤니티 게시물의 권리 기준을 정리합니다." },
} as const;

export function LegalPage() {
  const location = useLocation();
  const section = legalSections[location.pathname as keyof typeof legalSections] ?? legalSections["/legal/terms"];
  const Icon = section.icon;

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[980px] space-y-6 px-4 py-10 sm:px-6">
        <section className="rounded-2xl border border-slate-200 bg-white p-6">
          <Badge className="bg-slate-100 text-slate-700">{section.badge}</Badge>
          <h1 className="mt-3 flex items-center gap-2 text-3xl font-black text-slate-900">
            <Icon className="size-7 text-slate-700" />
            {section.title}
          </h1>
          <p className="mt-2 text-sm leading-relaxed text-slate-600">{section.desc}</p>
        </section>

        <Card className="border border-slate-200 bg-white">
          <CardHeader>
            <CardTitle className="text-base">문서 구조</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {["목적과 적용 범위", "수집/처리 항목", "보관 기간과 파기", "사용자 권리와 문의", "개정 공지 절차"].map((item) => (
              <div key={item} className="rounded-xl bg-slate-50 p-4 text-sm font-semibold text-slate-700">{item}</div>
            ))}
          </CardContent>
        </Card>

        <div className="rounded-xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-800">
          AI 데이터 동의 상세 설정은 <Link to="/settings?tab=ai-consent" className="font-bold underline">설정 화면</Link>과 연결됩니다.
        </div>
      </div>
    </div>
  );
}
