import { useSearchParams } from "react-router";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { Bell, Database, Lock, Mail, Shield, Smartphone, ToggleRight, UserCog } from "lucide-react";

const tabs = ["account", "privacy", "ai-consent", "notifications"] as const;
type SettingsTab = (typeof tabs)[number];

export function SettingsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab") ?? "account";
  const activeTab: SettingsTab = tabs.includes(requestedTab as SettingsTab) ? (requestedTab as SettingsTab) : "account";

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1200px] space-y-6 px-4 py-8 sm:px-6">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
            <UserCog className="size-6 text-blue-600" />
            설정
          </h1>
          <p className="mt-1 text-sm text-slate-500">계정, 개인정보, AI 데이터 사용 동의, 알림을 관리합니다</p>
        </div>

        <Tabs value={activeTab} onValueChange={(value) => setSearchParams({ tab: value })}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-white p-1">
            <TabsTrigger value="account">계정 설정</TabsTrigger>
            <TabsTrigger value="privacy">개인정보 관리</TabsTrigger>
            <TabsTrigger value="ai-consent">AI 데이터 사용 동의</TabsTrigger>
            <TabsTrigger value="notifications">알림 설정</TabsTrigger>
          </TabsList>

          <TabsContent value="account" className="mt-5 space-y-4">
            <Card className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <Mail className="size-4 text-blue-600" />
                  로그인 정보
                </CardTitle>
              </CardHeader>
              <CardContent className="grid gap-4 md:grid-cols-2">
                <Input value="jiwon.kim@careertuner.dev" readOnly />
                <Input value="김지원" readOnly />
                <Button variant="outline">이메일 변경</Button>
                <Button variant="outline">비밀번호 변경</Button>
              </CardContent>
            </Card>
            <Card className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <Lock className="size-4 text-slate-600" />
                  보안
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {["2단계 인증", "로그인 기기 관리", "휴면 계정 전환 설정"].map((item) => (
                  <div key={item} className="flex items-center justify-between rounded-xl bg-slate-50 p-4">
                    <span className="text-sm font-semibold text-slate-700">{item}</span>
                    <Button variant="outline" size="sm">관리</Button>
                  </div>
                ))}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="privacy" className="mt-5 space-y-4">
            <Card className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <Shield className="size-4 text-green-600" />
                  개인정보 관리
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {["프로필 공개 범위", "커뮤니티 익명 표시", "지원 기록 보관 기간", "계정 데이터 내려받기", "회원 탈퇴"].map((item) => (
                  <div key={item} className="flex items-center justify-between rounded-xl border border-slate-200 p-4">
                    <span className="text-sm font-semibold text-slate-700">{item}</span>
                    <Button variant="outline" size="sm">설정</Button>
                  </div>
                ))}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="ai-consent" className="mt-5">
            <Card className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <Database className="size-4 text-purple-600" />
                  AI 데이터 사용 동의
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {[
                  { label: "공고문/이력서 분석을 위한 AI 처리", required: true },
                  { label: "면접 답변 품질 개선을 위한 학습 데이터 활용", required: false },
                  { label: "장기 취업 경향 분석 리포트 생성", required: false },
                  { label: "서비스 품질 개선을 위한 익명 통계 활용", required: false },
                ].map((item) => (
                  <div key={item.label} className="flex items-center justify-between rounded-xl border border-slate-200 p-4">
                    <div>
                      <div className="text-sm font-semibold text-slate-800">{item.label}</div>
                      {item.required && <Badge className="mt-1 bg-blue-100 text-blue-700">필수</Badge>}
                    </div>
                    <ToggleRight className="size-8 text-blue-600" />
                  </div>
                ))}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="notifications" className="mt-5">
            <Card className="border border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="flex items-center gap-2 text-base">
                  <Bell className="size-4 text-amber-600" />
                  알림 설정
                </CardTitle>
              </CardHeader>
              <CardContent className="grid gap-3 md:grid-cols-2">
                {[
                  { label: "공고 분석 완료", icon: Bell },
                  { label: "면접 연습 리마인드", icon: Smartphone },
                  { label: "크레딧 부족 알림", icon: Bell },
                  { label: "커뮤니티 댓글/좋아요", icon: Bell },
                  { label: "결제/구독 안내", icon: Bell },
                  { label: "마케팅 정보 수신", icon: Mail },
                ].map((item) => (
                  <div key={item.label} className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50 p-4">
                    <div className="flex items-center gap-2">
                      <item.icon className="size-4 text-slate-500" />
                      <span className="text-sm font-semibold text-slate-700">{item.label}</span>
                    </div>
                    <ToggleRight className="size-7 text-blue-600" />
                  </div>
                ))}
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
