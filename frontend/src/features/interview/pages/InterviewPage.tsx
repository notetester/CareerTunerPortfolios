import { useState } from "react";
import { useSearchParams } from "react-router";
import { MessageSquare } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/app/components/ui/tabs";
import { useAuth } from "@/app/auth/AuthContext";
import { useApplicationCases } from "@/features/applications/hooks/useApplicationCases";
import { LoginRequiredState } from "@/features/applications/components/LoginRequiredState";
import { ModeSelectTab } from "../components/ModeSelectTab";
import { AutoSetupPanel } from "../components/AutoSetupPanel";
import { ExpectedQuestionsTab } from "../components/ExpectedQuestionsTab";
import { PracticeTab } from "../components/PracticeTab";
import { RealtimeInterviewTab } from "../components/RealtimeInterviewTab";
import { AvatarTab } from "../components/AvatarTab";
import { EvaluationCriteriaTab } from "../components/EvaluationCriteriaTab";
import { CorrectionInfoTab } from "../components/CorrectionInfoTab";
import { InterviewReportTab } from "../components/InterviewReportTab";
import type { InterviewMode, InterviewSession } from "../types/interview";

const INTERVIEW_TABS = [
  "modes",
  "questions",
  "practice",
  "live",
  "avatar",
  "evaluation",
  "correction",
  "report",
] as const;
type InterviewTab = (typeof INTERVIEW_TABS)[number];

export function InterviewPage() {
  const { isAuthenticated } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  // 모드 선택 탭과 질문/리포트 탭이 공유하는 상태.
  const [selectedMode, setSelectedMode] = useState<InterviewMode | null>(null);
  const [selectedCaseId, setSelectedCaseId] = useState<number | null>(null);
  const [activeSession, setActiveSession] = useState<InterviewSession | null>(null);
  // 홈 마누스 검색창에서 넘어온 요청(자동 셋업 진입).
  const [autoPrompt] = useState(() => sessionStorage.getItem("interview.autoPrompt") ?? "");

  const cases = useApplicationCases(isAuthenticated);

  const requested = searchParams.get("tab") ?? "modes";
  const activeTab: InterviewTab = INTERVIEW_TABS.includes(requested as InterviewTab)
    ? (requested as InterviewTab)
    : "modes";
  const autoMode = searchParams.get("auto") === "1" && autoPrompt.length > 0;

  const goTab = (tab: string) => setSearchParams(tab === "modes" ? {} : { tab });

  if (!isAuthenticated) {
    return (
      <LoginRequiredState
        title="로그인이 필요합니다"
        description="AI 가상 면접은 로그인 후 이용할 수 있습니다."
      />
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-[1400px] space-y-8 px-6 py-8">
        <div>
          <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
            <MessageSquare className="size-6 text-blue-600" />
            AI 가상 면접
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            지원 건과 모드를 고르면 AI가 질문을 만들고, 답변을 평가해 리포트를 제공합니다
          </p>
        </div>

        <Tabs value={activeTab} onValueChange={goTab}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-white p-1">
            <TabsTrigger value="modes">면접 모드 선택</TabsTrigger>
            <TabsTrigger value="questions">예상 면접 질문</TabsTrigger>
            <TabsTrigger value="practice">복습 테스트</TabsTrigger>
            <TabsTrigger value="live">음성 모의면접</TabsTrigger>
            <TabsTrigger value="avatar">아바타 화상 면접</TabsTrigger>
            <TabsTrigger value="evaluation">답변 평가 기준</TabsTrigger>
            <TabsTrigger value="correction">AI 첨삭</TabsTrigger>
            <TabsTrigger value="report">면접 리포트</TabsTrigger>
          </TabsList>

          <TabsContent value="modes" className="mt-6">
            {autoMode ? (
              <AutoSetupPanel
                cases={cases.applicationCases}
                casesLoading={cases.loading}
                prompt={autoPrompt}
                onReady={(session) => {
                  setActiveSession(session);
                  sessionStorage.removeItem("interview.autoPrompt");
                  goTab("practice");
                }}
                onManual={() => setSearchParams({})}
              />
            ) : (
              <ModeSelectTab
                cases={cases.applicationCases}
                casesLoading={cases.loading}
                casesError={cases.error}
                selectedCaseId={selectedCaseId}
                selectedMode={selectedMode}
                onSelectCase={setSelectedCaseId}
                onSelectMode={setSelectedMode}
                onSessionStarted={(session) => {
                  setActiveSession(session);
                  goTab("questions");
                }}
              />
            )}
          </TabsContent>

          <TabsContent value="questions" className="mt-6">
            <ExpectedQuestionsTab session={activeSession} onGoToPractice={() => goTab("practice")} />
          </TabsContent>

          <TabsContent value="practice" className="mt-6">
            <PracticeTab session={activeSession} onGoToReport={() => goTab("report")} />
          </TabsContent>

          <TabsContent value="live" className="mt-6">
            <RealtimeInterviewTab session={activeSession} />
          </TabsContent>

          <TabsContent value="avatar" className="mt-6">
            <AvatarTab />
          </TabsContent>

          <TabsContent value="evaluation" className="mt-6">
            <EvaluationCriteriaTab />
          </TabsContent>

          <TabsContent value="correction" className="mt-6">
            <CorrectionInfoTab />
          </TabsContent>

          <TabsContent value="report" className="mt-6">
            <InterviewReportTab session={activeSession} />
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
