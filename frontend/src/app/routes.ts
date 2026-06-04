import { createBrowserRouter } from "react-router";
import { Root } from "./components/layout/Root";
import { HomePage } from "./pages/Home";
import { DashboardPage } from "./pages/Dashboard";
import { ApplicationsPage } from "./pages/Applications";
import { ApplicationDetailPage } from "./pages/ApplicationDetail";
import { AIInterviewPage } from "./pages/AIInterview";
import { AnalysisPage } from "./pages/Analysis";
import { CommunityPage } from "./pages/Community";
import { PricingPage } from "./pages/Pricing";
import { ProfilePage } from "./pages/Profile";
import { LoginPage } from "./pages/Login";

export const router = createBrowserRouter([
  {
    path: "/",
    Component: Root,
    children: [
      { index: true, Component: HomePage },
      { path: "dashboard", Component: DashboardPage },
      { path: "applications", Component: ApplicationsPage },
      { path: "applications/:id", Component: ApplicationDetailPage },
      { path: "interview", Component: AIInterviewPage },
      { path: "analysis", Component: AnalysisPage },
      { path: "community", Component: CommunityPage },
      { path: "pricing", Component: PricingPage },
      { path: "profile", Component: ProfilePage },
      { path: "login", Component: LoginPage },
    ],
  },
]);
