import { Navigate, useNavigate, useParams } from "react-router";
import NoticeDetailPage from "./NoticeDetailPage";

export default function NoticeRouteDetailPage() {
  const navigate = useNavigate();
  const { noticeId } = useParams();
  const parsedNoticeId = Number(noticeId);

  if (!Number.isInteger(parsedNoticeId) || parsedNoticeId <= 0) {
    return <Navigate to="/support/notices" replace />;
  }

  return (
    <NoticeDetailPage
      noticeId={parsedNoticeId}
      onBack={() => navigate("/support/notices")}
      onNavigate={(id) => navigate(`/support/notices/${id}`, { replace: true })}
    />
  );
}
