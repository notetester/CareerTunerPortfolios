import { useEffect, useState } from "react";
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
import { useTutorialStore } from "../tutorial/tutorialStore";
import { dummySession } from "../tutorial/dummyData";
import { TutorialOverlay } from "../tutorial/TutorialOverlay";
import { TUT_STEPS } from "../tutorial/tutSteps";
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
  const mode = useTutorialStore((s) => s.mode);
  const tutStep = useTutorialStore((s) => s.step);
  const startTutorial = useTutorialStore((s) => s.startTutorial);
  const startDemo = useTutorialStore((s) => s.startDemo);
  const notifyTab = useTutorialStore((s) => s.notifyTab);
  const mockActive = mode !== "off"; // 데모/튜토리얼 둘 다 더미·게이트 우회
  const isTutorial = mode === "tutorial"; // 풍선·자동 진행은 튜토리얼만
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

  const wantTutorial = searchParams.get("tutorial") === "1";
  const wantDemo = searchParams.get("demo") === "1";

  // 데모/튜토리얼 모드에서는 실제 세션 없이도 더미 세션으로 흐름을 보여준다.
  const effectiveSession = mockActive ? (activeSession ?? dummySession) : activeSession;

  // ?tutorial=1 / ?demo=1 로 진입하면 해당 모드를 자동 시작한다.
  useEffect(() => {
    if (mode !== "off") return;
    if (wantTutorial) startTutorial();
    else if (wantDemo) startDemo();
  }, [wantTutorial, wantDemo, mode, startTutorial, startDemo]);

  // 튜토리얼: 현재 step 에 tab 이 지정돼 있으면 그 탭으로 자동 전환한다.
  useEffect(() => {
    if (!isTutorial) return;
    const target = TUT_STEPS[tutStep]?.tab;
    if (target && target !== activeTab) goTab(target);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isTutorial, tutStep]);

  // 튜토리얼: 사용자의 탭 이동을 엔진에 알린다 (awaitTab step 자동 진행).
  useEffect(() => {
    if (isTutorial) notifyTab(activeTab);
  }, [activeTab, isTutorial, notifyTab]);

  if (!isAuthenticated && !mockActive && !wantTutorial && !wantDemo) {
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
        <div className="flex items-start justify-between gap-4">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <MessageSquare className="size-6 text-blue-600" />
              AI 가상 면접
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              지원 건과 모드를 고르면 AI가 질문을 만들고, 답변을 평가해 리포트를 제공합니다
            </p>
          </div>
          <div className="flex shrink-0 gap-2">
            <button
              onClick={startDemo}
              className="rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white transition-colors hover:bg-indigo-700"
            >
              체험하기
            </button>
            <button
              onClick={startTutorial}
              className="rounded-lg border border-indigo-200 bg-indigo-50 px-3 py-2 text-sm font-semibold text-indigo-600 transition-colors hover:bg-indigo-100"
            >
              둘러보기
            </button>
          </div>
        </div>

        <Tabs value={activeTab} onValueChange={goTab}>
          <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-white p-1">
            <TabsTrigger value="modes" data-tut="tut-tab-modes">면접 모드 선택</TabsTrigger>
            <TabsTrigger value="questions" data-tut="tut-tab-questions">예상 면접 질문</TabsTrigger>
            <TabsTrigger value="practice" data-tut="tut-tab-practice">복습 테스트</TabsTrigger>
            <TabsTrigger value="live" data-tut="tut-tab-live">음성 모의면접</TabsTrigger>
            <TabsTrigger value="avatar" data-tut="tut-tab-avatar">아바타 화상 면접</TabsTrigger>
            <TabsTrigger value="evaluation" data-tut="tut-tab-evaluation">답변 평가 기준</TabsTrigger>
            <TabsTrigger value="correction" data-tut="tut-tab-correction">AI 첨삭</TabsTrigger>
            <TabsTrigger value="report" data-tut="tut-tab-report">면접 리포트</TabsTrigger>
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
            <ExpectedQuestionsTab session={effectiveSession} onGoToPractice={() => goTab("practice")} />
          </TabsContent>

          <TabsContent value="practice" className="mt-6">
            <PracticeTab session={effectiveSession} onGoToReport={() => goTab("report")} />
          </TabsContent>

          <TabsContent value="live" className="mt-6">
            <RealtimeInterviewTab session={effectiveSession} />
          </TabsContent>

          <TabsContent value="avatar" className="mt-6">
            <AvatarTab session={effectiveSession} />
          </TabsContent>

          <TabsContent value="evaluation" className="mt-6">
            <EvaluationCriteriaTab />
          </TabsContent>

          <TabsContent value="correction" className="mt-6">
            <CorrectionInfoTab />
          </TabsContent>

          <TabsContent value="report" className="mt-6">
            <InterviewReportTab session={effectiveSession} />
          </TabsContent>
        </Tabs>
      </div>
      <TutorialOverlay />
    </div>
  );
}
