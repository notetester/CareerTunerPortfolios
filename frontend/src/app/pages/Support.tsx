import { useLocation } from "react-router";
import SupportHomePage from "../../features/support/pages/SupportHomePage";
import GuidePage from "../../features/support/pages/GuidePage";
import FaqPage from "../../features/support/pages/FaqPage";
import NoticeListPage from "../../features/support/pages/NoticeListPage";
import { ContactPage } from "../../features/support/pages/ContactPage";

export function SupportPage() {
  const { pathname } = useLocation();
  if (pathname === "/support/guide") return <GuidePage />;
  if (pathname === "/support/faq") return <FaqPage />;
  if (pathname === "/support/notices") return <NoticeListPage />;
  if (pathname === "/support/contact") return <ContactPage />;
  return <SupportHomePage />;
}
