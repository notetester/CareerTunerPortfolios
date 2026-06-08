import { useLocation } from "react-router";
import { ApplicationListPage } from "@/features/applications/pages/ApplicationListPage";
import { NewApplicationPage } from "@/features/applications/pages/NewApplicationPage";

export function ApplicationsPage() {
  const location = useLocation();

  if (location.pathname.endsWith("/new")) {
    return <NewApplicationPage />;
  }

  return <ApplicationListPage />;
}
