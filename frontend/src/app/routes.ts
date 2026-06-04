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
import { BillingPage } from "./pages/Billing";
import { PricingPage } from "./pages/Pricing";
import { ProfilePage } from "./pages/Profile";
import { SettingsPage } from "./pages/Settings";
import { ServiceInfoPage } from "./pages/ServiceInfo";
import { SupportPage } from "./pages/Support";
import { CompanyPage } from "./pages/Company";
import { LegalPage } from "./pages/Legal";
import { LoginPage } from "./pages/Login";
import { AuthCallbackPage } from "./pages/AuthCallback";
import { VerifyEmailResultPage } from "./pages/VerifyEmailResult";

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
      { path: "community", Component: CommunityPage },
      { path: "billing", Component: BillingPage },
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
      { path: "auth/verify-email/result", Component: VerifyEmailResultPage },
    ],
  },
]);
