import { createBrowserRouter } from "react-router";
import { Root } from "./components/layout/Root";
import { HomePage } from "./pages/Home";
import { DashboardPage } from "./pages/Dashboard";
import { ApplicationsPage } from "./pages/Applications";
import { ApplicationDetailPage } from "./pages/ApplicationDetail";
import { CareerRoadmapPage } from "@/features/analysis/pages/CareerRoadmapPage";
import { CertificateSearchPage } from "@/features/analysis/pages/CertificateSearchPage";
import { CommunityPage } from "./pages/Community";
import { MessengerPage } from "./pages/Messenger";
import { BillingPage } from "./pages/Billing";
import { BillingFailPage } from "./pages/BillingFail";
import { BillingSuccessPage } from "./pages/BillingSuccess";
import { PricingPage } from "./pages/Pricing";
import { ProfileAiAnalysisPage } from "./pages/Profile";
import { SettingsPage } from "./pages/Settings";
import { ServiceInfoPage } from "./pages/ServiceInfo";
import SupportHomePage from "@/features/support/pages/SupportHomePage";
import GuidePage from "@/features/support/pages/GuidePage";
import FaqPage from "@/features/support/pages/FaqPage";
import NoticeListPage from "@/features/support/pages/NoticeListPage";
import NoticeRouteDetailPage from "@/features/support/pages/NoticeRouteDetailPage";
import { ContactPage } from "@/features/support/pages/ContactPage";
import { ChatbotPage } from "@/features/support/pages/ChatbotPage";
import { CompanyPage } from "./pages/Company";
import { LegalPage } from "./pages/Legal";
import { NotFoundPage } from "./pages/NotFound";
import { LoginPage } from "./pages/Login";
import { AuthCallbackPage } from "./pages/AuthCallback";
import { SocialConsentPage } from "./pages/SocialConsent";
import { VerifyEmailResultPage } from "./pages/VerifyEmailResult";
import { ForgotPasswordPage } from "./pages/ForgotPassword";
import { FindIdPage } from "./pages/FindId";
import { ResetPasswordPage } from "./pages/ResetPassword";
import { ReleaseDormantPage } from "./pages/ReleaseDormant";
import { MfaLoginPage } from "./pages/MfaLogin";
import { MfaApprovalsPage } from "./pages/MfaApprovals";
import { adminRoutes } from "../admin/routes";
import NotificationPage from "@/features/notification/pages/NotificationPage";
import { CommunityActivityPage } from "@/features/community/pages/CommunityActivityPage";
import { CommunityUserActivityPage } from "@/features/community/pages/CommunityUserActivityPage";
import { ProfileDetailPage } from "@/features/profile/pages/ProfileDetailPage";
import { RewardsPage } from "@/features/rewards/pages/RewardsPage";
// W1: 기업 서비스 허브(신청/내 공고 관리) + 공개 채용공고 게시판
import { CompanyHubPage, CompanyServiceOverviewPage } from "@/features/company/pages/CompanyHubPage";
import { JobBoardPage } from "@/features/jobboard/pages/JobBoardPage";
import { JobDetailPage } from "@/features/jobboard/pages/JobDetailPage";
import {
  ProfileBasicPage,
  ProfileCredentialsPage,
  ProfileExperiencePage,
  ProfileHubPage,
  ProfileResumePage,
  ProfileSelfIntroductionPage,
  ProfileSkillsPage,
} from "@/features/profile/pages/ProfileSectionPages";
import { ApplicationHubPage } from "@/features/applications/pages/ApplicationHubPage";
import { JobAnalysisPage } from "@/features/applications/pages/JobAnalysisPage";
import { CatalogHubPage, CertificateCatalogPage, NcsCatalogPage } from "@/features/catalog/pages/CatalogSectionPages";
import { InterviewHubPage } from "@/features/interview/pages/InterviewHubPage";
import {
  InterviewAvatarPage,
  InterviewEvaluationPage,
  InterviewLivePage,
  InterviewModesPage,
  InterviewPracticePage,
  InterviewQuestionsPage,
  InterviewReportPage,
} from "@/features/interview/pages/InterviewPage";
import { CorrectionHubPage } from "@/features/correction/pages/CorrectionHubPage";
import {
  AnswerCorrectionPage,
  CoverLetterCorrectionPage,
  PortfolioCorrectionPage,
  ResumeCorrectionPage,
} from "@/features/correction/pages/CorrectionSectionPages";
import { AnalysisHubPage } from "@/features/analysis/pages/AnalysisHubPage";
import {
  AnalysisReadinessPage,
  AnalysisRecommendationsPage,
  AnalysisScoresPage,
  AnalysisTrendsPage,
  AnalysisWeaknessesPage,
} from "@/features/analysis/pages/AnalysisSectionPages";
import { PlannerHubPage } from "@/features/planner/pages/PlannerHubPage";
import { PlannerMemosPage, PlannerOverlaysPage, PlannerSchedulePage } from "@/features/planner/pages/PlannerSectionPages";
import { MessengerOverviewPage } from "@/features/collaboration/pages/MessengerOverviewPage";
import { ServiceAboutPage } from "@/features/service/pages/ServiceAboutPage";
import {
  ApplicationComparePage,
  ApplicationLearningPage,
  ApplicationStrategyPage,
} from "@/features/applications/pages/ApplicationInsightsPage";
import { MobileSessionsPage } from "@/features/interview/pages/MobileSessionsPage";
import { MobileSessionThreadPage } from "@/features/interview/pages/MobileSessionThreadPage";
import { MicRemotePage } from "@/features/interview/pages/MicRemotePage";
import { withAuthGate, withConsentGate } from "./auth/ConsentGate";

const basename = import.meta.env.BASE_URL === "/"
  ? "/"
  : import.meta.env.BASE_URL.replace(/\/$/, "");

const ConsentInterviewHubPage = withConsentGate(InterviewHubPage, ["AI_DATA"]);
const ConsentInterviewModesPage = withConsentGate(InterviewModesPage, ["AI_DATA"]);
const ConsentInterviewQuestionsPage = withConsentGate(InterviewQuestionsPage, ["AI_DATA"]);
const ConsentInterviewPracticePage = withConsentGate(InterviewPracticePage, ["AI_DATA"]);
const ConsentInterviewLivePage = withConsentGate(InterviewLivePage, ["AI_DATA"]);
const ConsentInterviewAvatarPage = withConsentGate(InterviewAvatarPage, ["AI_DATA"]);
const ConsentInterviewEvaluationPage = withConsentGate(InterviewEvaluationPage, ["AI_DATA"]);
const ConsentInterviewReportPage = withConsentGate(InterviewReportPage, ["AI_DATA"]);
const ConsentMobileSessionsPage = withConsentGate(MobileSessionsPage, ["AI_DATA"]);
const ConsentMobileSessionThreadPage = withConsentGate(MobileSessionThreadPage, ["AI_DATA"]);
const ConsentMicRemotePage = withConsentGate(MicRemotePage, ["AI_DATA"]);
const ConsentCorrectionHubPage = withConsentGate(CorrectionHubPage, ["AI_DATA"]);
const ConsentAnswerCorrectionPage = withConsentGate(AnswerCorrectionPage, ["AI_DATA"]);
const ConsentCoverLetterCorrectionPage = withConsentGate(CoverLetterCorrectionPage, ["AI_DATA"]);
const ConsentResumeCorrectionPage = withConsentGate(ResumeCorrectionPage, ["AI_DATA"]);
const ConsentPortfolioCorrectionPage = withConsentGate(PortfolioCorrectionPage, ["AI_DATA"]);
const ConsentAnalysisHubPage = withConsentGate(AnalysisHubPage, ["AI_DATA"]);
const ConsentAnalysisTrendsPage = withConsentGate(AnalysisTrendsPage, ["AI_DATA"]);
const ConsentAnalysisWeaknessesPage = withConsentGate(AnalysisWeaknessesPage, ["AI_DATA"]);
const ConsentAnalysisReadinessPage = withConsentGate(AnalysisReadinessPage, ["AI_DATA"]);
const ConsentAnalysisScoresPage = withConsentGate(AnalysisScoresPage, ["AI_DATA"]);
const ConsentAnalysisRecommendationsPage = withConsentGate(AnalysisRecommendationsPage, ["AI_DATA"]);
const AuthenticatedApplicationsPage = withAuthGate(ApplicationsPage);
const AuthenticatedApplicationHubPage = withAuthGate(ApplicationHubPage);
const AuthenticatedJobAnalysisPage = withAuthGate(JobAnalysisPage);
const AuthenticatedApplicationDetailPage = withAuthGate(ApplicationDetailPage);
// 지원 건 관리 하위메뉴 — 전체 지원 건 집계 화면(적합도 비교·전략·학습/자격증). 각각 독립 페이지.
const AuthenticatedApplicationComparePage = withAuthGate(ApplicationComparePage);
const AuthenticatedApplicationStrategyPage = withAuthGate(ApplicationStrategyPage);
const AuthenticatedApplicationLearningPage = withAuthGate(ApplicationLearningPage);
const AuthenticatedMfaApprovalsPage = withAuthGate(MfaApprovalsPage);
const AuthenticatedMessengerPage = withAuthGate(MessengerPage);
const AuthenticatedMessengerOverviewPage = withAuthGate(MessengerOverviewPage);
const AuthenticatedCommunityActivityPage = withAuthGate(CommunityActivityPage);
const AuthenticatedRewardsPage = withAuthGate(RewardsPage);
const AuthenticatedBillingPage = withAuthGate(BillingPage);
const AuthenticatedBillingSuccessPage = withAuthGate(BillingSuccessPage);
const AuthenticatedBillingFailPage = withAuthGate(BillingFailPage);
const AuthenticatedCompanyOverviewPage = withAuthGate(CompanyServiceOverviewPage);
const AuthenticatedCompanyHubPage = withAuthGate(CompanyHubPage);
const AuthenticatedNotificationPage = withAuthGate(NotificationPage);
const AuthenticatedProfileHubPage = withAuthGate(ProfileHubPage);
const AuthenticatedProfileBasicPage = withAuthGate(ProfileBasicPage);
const AuthenticatedProfileResumePage = withAuthGate(ProfileResumePage);
const AuthenticatedProfileSelfIntroductionPage = withAuthGate(ProfileSelfIntroductionPage);
const AuthenticatedProfileExperiencePage = withAuthGate(ProfileExperiencePage);
const AuthenticatedProfileSkillsPage = withAuthGate(ProfileSkillsPage);
const AuthenticatedProfileCredentialsPage = withAuthGate(ProfileCredentialsPage);
const AuthenticatedProfileAiAnalysisPage = withAuthGate(ProfileAiAnalysisPage);
const AuthenticatedProfileDetailPage = withAuthGate(ProfileDetailPage);
const AuthenticatedSettingsPage = withAuthGate(SettingsPage);
const ConsentDashboardPage = withConsentGate(DashboardPage, ["AI_DATA"]);
const AuthenticatedPlannerHubPage = withAuthGate(PlannerHubPage);
const AuthenticatedPlannerSchedulePage = withAuthGate(PlannerSchedulePage);
const AuthenticatedPlannerMemosPage = withAuthGate(PlannerMemosPage);
const AuthenticatedPlannerOverlaysPage = withAuthGate(PlannerOverlaysPage);
const ConsentCareerRoadmapPage = withConsentGate(CareerRoadmapPage, ["AI_DATA"]);
const AuthenticatedCertificateSearchPage = withAuthGate(CertificateSearchPage);

export const router = createBrowserRouter([
  {
    path: "/",
    Component: Root,
    children: [
      { index: true, Component: HomePage },
      { path: "home", Component: HomePage },
      { path: "dashboard", Component: ConsentDashboardPage },
      { path: "applications", Component: AuthenticatedApplicationHubPage },
      { path: "applications/list", Component: AuthenticatedApplicationsPage },
      { path: "applications/new", Component: AuthenticatedApplicationsPage },
      { path: "applications/trash", Component: AuthenticatedApplicationsPage },
      // 정적 경로 — createBrowserRouter 는 정적 세그먼트를 :id 보다 우선 매칭한다.
      { path: "applications/compare", Component: AuthenticatedApplicationComparePage },
      { path: "applications/strategy", Component: AuthenticatedApplicationStrategyPage },
      { path: "applications/learning", Component: AuthenticatedApplicationLearningPage },
      { path: "applications/:id/:section/:mode", Component: AuthenticatedApplicationDetailPage },
      { path: "applications/:id/:section", Component: AuthenticatedApplicationDetailPage },
      { path: "applications/:id", Component: AuthenticatedApplicationDetailPage },
      { path: "job-analysis", Component: AuthenticatedJobAnalysisPage },
      { path: "interview", Component: ConsentInterviewHubPage },
      { path: "interview/modes", Component: ConsentInterviewModesPage },
      { path: "interview/questions", Component: ConsentInterviewQuestionsPage },
      { path: "interview/practice", Component: ConsentInterviewPracticePage },
      { path: "interview/live", Component: ConsentInterviewLivePage },
      { path: "interview/avatar", Component: ConsentInterviewAvatarPage },
      { path: "interview/evaluation", Component: ConsentInterviewEvaluationPage },
      { path: "interview/reports", Component: ConsentInterviewReportPage },
      // 모바일 세션 스레드(Claude 앱 문법) — 하단 탭 "세션" + 디스패치 딥링크 진입 (interview 소유, D)
      { path: "m/sessions", Component: ConsentMobileSessionsPage },
      { path: "m/mfa-approvals", Component: AuthenticatedMfaApprovalsPage },
      { path: "m/session/:id", Component: ConsentMobileSessionThreadPage },
      // 폰 마이크 핸드오프 송신 페이지 — 데스크탑 음성면접의 원격 마이크 (interview 소유, D)
      { path: "mic-remote", Component: ConsentMicRemotePage },
      { path: "correction", Component: ConsentCorrectionHubPage },
      { path: "correction/answer", Component: ConsentAnswerCorrectionPage },
      { path: "correction/cover-letter", Component: ConsentCoverLetterCorrectionPage },
      { path: "correction/resume", Component: ConsentResumeCorrectionPage },
      { path: "correction/portfolio", Component: ConsentPortfolioCorrectionPage },
      { path: "analysis", Component: ConsentAnalysisHubPage },
      { path: "analysis/trends", Component: ConsentAnalysisTrendsPage },
      { path: "analysis/weaknesses", Component: ConsentAnalysisWeaknessesPage },
      { path: "analysis/readiness", Component: ConsentAnalysisReadinessPage },
      { path: "analysis/interview-scores", Component: ConsentAnalysisScoresPage },
      { path: "analysis/recommendations", Component: ConsentAnalysisRecommendationsPage },
      { path: "planner", Component: AuthenticatedPlannerHubPage },
      { path: "planner/schedule", Component: AuthenticatedPlannerSchedulePage },
      { path: "planner/memos", Component: AuthenticatedPlannerMemosPage },
      { path: "planner/overlays", Component: AuthenticatedPlannerOverlaysPage },
      { path: "career-roadmap", Component: ConsentCareerRoadmapPage },
      { path: "certificates", Component: AuthenticatedCertificateSearchPage },
      { path: "messenger", Component: AuthenticatedMessengerOverviewPage },
      { path: "messenger/rooms", Component: AuthenticatedMessengerPage },
      { path: "messenger/discover", Component: AuthenticatedMessengerPage },
      { path: "messenger/friends", Component: AuthenticatedMessengerPage },
      { path: "community", Component: CommunityPage },
      { path: "community/popular", Component: CommunityPage },
      { path: "community/guidelines", Component: CommunityPage },
      // 알림/딥링크용 글 상세 경로. 같은 CommunityPage가 :postId를 읽어 상세 뷰를 연다. (팀장 승인 2026-06-19)
      { path: "community/posts/:postId", Component: CommunityPage },
      // 커뮤니티 활동 — 내 활동(6탭+반응 유지 설정), 타인 프로필 활동(공개범위 적용)
      { path: "community/activity", Component: AuthenticatedCommunityActivityPage },
      { path: "community/users/:userId/activity", Component: CommunityUserActivityPage },
      // 내 정보 관리 — 닉네임 프로필·이력서 스펙·계정 확충
      { path: "profile/detail", Component: AuthenticatedProfileDetailPage },
      // 브라우저 소셜 계정 연결 전용 반환 경로. verified App Link 경로와 분리해
      // 설치 앱이 모바일 웹의 OAuth 결과를 가로채지 않게 한다.
      { path: "profile/social-callback", Component: AuthenticatedProfileDetailPage },
      { path: "profile/rewards", Component: AuthenticatedRewardsPage },
      { path: "billing", Component: AuthenticatedBillingPage },
      { path: "billing/plans", Component: AuthenticatedBillingPage },
      { path: "billing/usage", Component: AuthenticatedBillingPage },
      { path: "billing/credits", Component: AuthenticatedBillingPage },
      { path: "billing/history", Component: AuthenticatedBillingPage },
      { path: "billing/success", Component: AuthenticatedBillingSuccessPage },
      { path: "billing/fail", Component: AuthenticatedBillingFailPage },
      { path: "pricing", Component: PricingPage },
      { path: "profile", Component: AuthenticatedProfileHubPage },
      { path: "profile/basic", Component: AuthenticatedProfileBasicPage },
      { path: "profile/resume", Component: AuthenticatedProfileResumePage },
      { path: "profile/self-introduction", Component: AuthenticatedProfileSelfIntroductionPage },
      { path: "profile/experience", Component: AuthenticatedProfileExperiencePage },
      { path: "profile/skills", Component: AuthenticatedProfileSkillsPage },
      { path: "profile/credentials", Component: AuthenticatedProfileCredentialsPage },
      { path: "profile/ai-analysis", Component: AuthenticatedProfileAiAnalysisPage },
      { path: "settings", Component: AuthenticatedSettingsPage },
      { path: "settings/account", Component: AuthenticatedSettingsPage },
      { path: "settings/privacy", Component: AuthenticatedSettingsPage },
      { path: "settings/ai-consent", Component: AuthenticatedSettingsPage },
      { path: "settings/notifications", Component: AuthenticatedSettingsPage },
      { path: "settings/blocks", Component: AuthenticatedSettingsPage },
      { path: "settings/company", Component: AuthenticatedSettingsPage },
      { path: "features", Component: ServiceInfoPage },
      { path: "service/about", Component: ServiceAboutPage },
      { path: "support", Component: SupportHomePage },
      { path: "support/guide", Component: GuidePage },
      { path: "support/faq", Component: FaqPage },
      { path: "support/notices", Component: NoticeListPage },
      { path: "support/notices/:noticeId", Component: NoticeRouteDetailPage },
      { path: "support/contact", Component: ContactPage },
      { path: "support/chat", Component: ChatbotPage },
      // 기업 서비스 대분류와 실제 신청/공고 관리 화면을 분리한다(아래 company/* 소개 페이지와도 별개).
      { path: "company", Component: AuthenticatedCompanyOverviewPage },
      { path: "company/manage", Component: AuthenticatedCompanyHubPage },
      // 공개 채용공고 게시판
      { path: "jobs", Component: JobBoardPage },
      { path: "jobs/:id", Component: JobDetailPage },
      // NCS·자격증 통합 카탈로그 검색(공개)
      { path: "catalog", Component: CatalogHubPage },
      { path: "catalog/ncs", Component: NcsCatalogPage },
      { path: "catalog/certificates", Component: CertificateCatalogPage },
      { path: "company/about", Component: CompanyPage },
      { path: "company/team", Component: CompanyPage },
      { path: "company/careers", Component: CompanyPage },
      { path: "company/blog", Component: CompanyPage },
      { path: "company/press", Component: CompanyPage },
      { path: "company/social", Component: CompanyPage },
      { path: "company/social/:channel", Component: CompanyPage },
      { path: "legal/terms", Component: LegalPage },
      { path: "legal/privacy", Component: LegalPage },
      { path: "legal/marketing", Component: LegalPage },
      { path: "legal/ai-data-consent", Component: LegalPage },
      { path: "legal/resume-analysis-consent", Component: LegalPage },
      { path: "legal/copyright", Component: LegalPage },
      { path: "login", Component: LoginPage },
      { path: "auth/callback", Component: AuthCallbackPage },
      { path: "auth/browser-callback", Component: AuthCallbackPage },
      { path: "auth/social-consent", Component: SocialConsentPage },
      { path: "auth/verify-email/result", Component: VerifyEmailResultPage },
      { path: "auth/forgot-password", Component: ForgotPasswordPage },
      { path: "auth/find-id", Component: FindIdPage },
      { path: "auth/find-id/result", Component: FindIdPage },
      { path: "auth/reset-password", Component: ResetPasswordPage },
      { path: "auth/release-dormant", Component: ReleaseDormantPage },
      { path: "auth/mfa", Component: MfaLoginPage },
      { path: "notifications", Component: AuthenticatedNotificationPage },
      ...adminRoutes,
      // catch-all 404 — 죽은 링크가 라우터 기본 오류 화면 대신 스타일된 안내로 떨어진다.
      { path: "*", Component: NotFoundPage },
    ],
  },
], { basename });
