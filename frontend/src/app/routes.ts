import { createBrowserRouter } from "react-router";
import { Root } from "./components/layout/Root";
import { HomePage } from "./pages/Home";
import { DashboardPage } from "./pages/Dashboard";
import { ApplicationsPage } from "./pages/Applications";
import { ApplicationDetailPage } from "./pages/ApplicationDetail";
import { AIInterviewPage } from "./pages/AIInterview";
import { CorrectionPage } from "./pages/Correction";
import { AnalysisPage } from "./pages/Analysis";
import { CommunityPage } from "./pages/Community";
import { MessengerPage } from "./pages/Messenger";
import { BillingPage } from "./pages/Billing";
import { BillingFailPage } from "./pages/BillingFail";
import { BillingSuccessPage } from "./pages/BillingSuccess";
import { PricingPage } from "./pages/Pricing";
import { ProfilePage } from "./pages/Profile";
import { SettingsPage } from "./pages/Settings";
import { ServiceInfoPage } from "./pages/ServiceInfo";
import { SupportPage } from "./pages/Support";
import { CompanyPage } from "./pages/Company";
import { LegalPage } from "./pages/Legal";
import { LoginPage } from "./pages/Login";
import { AuthCallbackPage } from "./pages/AuthCallback";
import { SocialConsentPage } from "./pages/SocialConsent";
import { VerifyEmailResultPage } from "./pages/VerifyEmailResult";
import { ForgotPasswordPage } from "./pages/ForgotPassword";
import { ResetPasswordPage } from "./pages/ResetPassword";
import { ReleaseDormantPage } from "./pages/ReleaseDormant";
import { adminRoutes } from "../admin/routes";
import NotificationPage from "@/features/notification/pages/NotificationPage";
import { CommunityActivityPage } from "@/features/community/pages/CommunityActivityPage";
import { CommunityUserActivityPage } from "@/features/community/pages/CommunityUserActivityPage";
import { ProfileDetailPage } from "@/features/profile/pages/ProfileDetailPage";
// W1: 기업 서비스 허브(신청/내 공고 관리) + 공개 채용공고 게시판
import { CompanyHubPage } from "@/features/company/pages/CompanyHubPage";
import { JobBoardPage } from "@/features/jobboard/pages/JobBoardPage";
import { JobDetailPage } from "@/features/jobboard/pages/JobDetailPage";
import { MobileSessionsPage } from "@/features/interview/pages/MobileSessionsPage";
import { MobileSessionThreadPage } from "@/features/interview/pages/MobileSessionThreadPage";
import { MicRemotePage } from "@/features/interview/pages/MicRemotePage";

const basename = import.meta.env.BASE_URL === "/"
  ? "/"
  : import.meta.env.BASE_URL.replace(/\/$/, "");

export const router = createBrowserRouter([
  {
    path: "/",
    Component: Root,
    children: [
      { index: true, Component: HomePage },
      { path: "home", Component: HomePage },
      { path: "dashboard", Component: DashboardPage },
      { path: "applications", Component: ApplicationsPage },
      { path: "applications/new", Component: ApplicationsPage },
      { path: "applications/trash", Component: ApplicationsPage },
      { path: "applications/:id/:section/:mode", Component: ApplicationDetailPage },
      { path: "applications/:id/:section", Component: ApplicationDetailPage },
      { path: "applications/:id", Component: ApplicationDetailPage },
      { path: "interview", Component: AIInterviewPage },
      // 모바일 세션 스레드(Claude 앱 문법) — 하단 탭 "세션" + 디스패치 딥링크 진입 (interview 소유, D)
      { path: "m/sessions", Component: MobileSessionsPage },
      { path: "m/session/:id", Component: MobileSessionThreadPage },
      // 폰 마이크 핸드오프 송신 페이지 — 데스크탑 음성면접의 원격 마이크 (interview 소유, D)
      { path: "mic-remote", Component: MicRemotePage },
      { path: "correction", Component: CorrectionPage },
      { path: "analysis", Component: AnalysisPage },
      { path: "messenger", Component: MessengerPage },
      { path: "community", Component: CommunityPage },
      // 알림/딥링크용 글 상세 경로. 같은 CommunityPage가 :postId를 읽어 상세 뷰를 연다. (팀장 승인 2026-06-19)
      { path: "community/posts/:postId", Component: CommunityPage },
      // 커뮤니티 활동 — 내 활동(6탭+반응 유지 설정), 타인 프로필 활동(공개범위 적용)
      { path: "community/activity", Component: CommunityActivityPage },
      { path: "community/users/:userId/activity", Component: CommunityUserActivityPage },
      // 내 정보 관리 — 닉네임 프로필·이력서 스펙·계정 확충
      { path: "profile/detail", Component: ProfileDetailPage },
      { path: "billing", Component: BillingPage },
      { path: "billing/success", Component: BillingSuccessPage },
      { path: "billing/fail", Component: BillingFailPage },
      { path: "pricing", Component: PricingPage },
      { path: "profile", Component: ProfilePage },
      { path: "settings", Component: SettingsPage },
      { path: "features", Component: ServiceInfoPage },
      { path: "service/about", Component: ServiceInfoPage },
      { path: "support", Component: SupportPage },
      { path: "support/guide", Component: SupportPage },
      { path: "support/faq", Component: SupportPage },
      { path: "support/notices", Component: SupportPage },
      { path: "support/contact", Component: SupportPage },
      { path: "support/chat", Component: SupportPage },
      // 기업 서비스 허브 — 기업 신청/내 공고 관리 (아래 company/* 소개 페이지와 별개)
      { path: "company", Component: CompanyHubPage },
      // 공개 채용공고 게시판
      { path: "jobs", Component: JobBoardPage },
      { path: "jobs/:id", Component: JobDetailPage },
      { path: "company/about", Component: CompanyPage },
      { path: "company/team", Component: CompanyPage },
      { path: "company/careers", Component: CompanyPage },
      { path: "company/blog", Component: CompanyPage },
      { path: "company/press", Component: CompanyPage },
      { path: "company/social", Component: CompanyPage },
      { path: "legal/terms", Component: LegalPage },
      { path: "legal/privacy", Component: LegalPage },
      { path: "legal/ai-data-consent", Component: LegalPage },
      { path: "legal/copyright", Component: LegalPage },
      { path: "login", Component: LoginPage },
      { path: "auth/callback", Component: AuthCallbackPage },
      { path: "auth/social-consent", Component: SocialConsentPage },
      { path: "auth/verify-email/result", Component: VerifyEmailResultPage },
      { path: "auth/forgot-password", Component: ForgotPasswordPage },
      { path: "auth/reset-password", Component: ResetPasswordPage },
      { path: "auth/release-dormant", Component: ReleaseDormantPage },
      { path: "notifications", Component: NotificationPage },
      ...adminRoutes,
    ],
  },
], { basename });
