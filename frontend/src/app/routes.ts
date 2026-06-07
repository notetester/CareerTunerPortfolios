import { createBrowserRouter } from "react-router";
import { Root } from "./components/layout/Root";
import { HomePage } from "./pages/Home";
import { DashboardPage } from "./pages/Dashboard";
import { ApplicationsPage } from "./pages/Applications";
import { ApplicationDetailPage } from "./pages/ApplicationDetail";
import { AIInterviewPage } from "./pages/AIInterview";
import { CorrectionPage } from "./pages/Correction";
import { AnalysisPage } from "./pages/Analysis";
import { CommunityHomePage } from "../features/community/pages/CommunityHomePage";
import { BillingPage } from "./pages/Billing";
import { PricingPage } from "./pages/Pricing";
import { ProfilePage } from "./pages/Profile";
import { SettingsPage } from "./pages/Settings";
import { ServiceInfoPage } from "../features/service/pages/ServiceInfoPage";
import SupportHomePage from "../features/support/pages/SupportHomePage";
import GuidePage from "../features/support/pages/GuidePage";
import FaqPage from "../features/support/pages/FaqPage";
import NoticeListPage from "../features/support/pages/NoticeListPage";
import { ContactPage } from "../features/support/pages/ContactPage";
import { CompanyPage } from "../features/company/pages/CompanyPage";
import LegalDocPage from "../features/legal/pages/LegalDocPage";
import NotificationPage from "../features/notification/pages/NotificationPage";
import { LoginPage } from "./pages/Login";
import { AuthCallbackPage } from "./pages/AuthCallback";
import { VerifyEmailResultPage } from "./pages/VerifyEmailResult";
import { adminRoutes } from "../admin/routes";

export const router = createBrowserRouter([
  {
    path: "/",
    Component: Root,
    children: [
      { index: true, Component: HomePage },
      { path: "dashboard", Component: DashboardPage },
      { path: "applications", Component: ApplicationsPage },
      { path: "applications/new", Component: ApplicationsPage },
      { path: "applications/:id", Component: ApplicationDetailPage },
      { path: "interview", Component: AIInterviewPage },
      { path: "correction", Component: CorrectionPage },
      { path: "analysis", Component: AnalysisPage },
      { path: "community", Component: CommunityHomePage },
      { path: "billing", Component: BillingPage },
      { path: "pricing", Component: PricingPage },
      { path: "profile", Component: ProfilePage },
      { path: "settings", Component: SettingsPage },
      { path: "features", Component: ServiceInfoPage },
      { path: "service/about", Component: ServiceInfoPage },
      { path: "support", Component: SupportHomePage },
      { path: "support/guide", Component: GuidePage },
      { path: "support/faq", Component: FaqPage },
      { path: "support/notices", Component: NoticeListPage },
      { path: "support/contact", Component: ContactPage },
      { path: "notifications", Component: NotificationPage },
      { path: "company/about", Component: CompanyPage },
      { path: "company/team", Component: CompanyPage },
      { path: "company/careers", Component: CompanyPage },
      { path: "company/blog", Component: CompanyPage },
      { path: "company/press", Component: CompanyPage },
      { path: "company/social", Component: CompanyPage },
      { path: "legal/terms", Component: LegalDocPage },
      { path: "legal/privacy", Component: LegalDocPage },
      { path: "legal/ai-data-consent", Component: LegalDocPage },
      { path: "legal/copyright", Component: LegalDocPage },
      { path: "login", Component: LoginPage },
      { path: "auth/callback", Component: AuthCallbackPage },
      { path: "auth/verify-email/result", Component: VerifyEmailResultPage },
      ...adminRoutes,
    ],
  },
]);
