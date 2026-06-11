import { Link } from "react-router";
import { Bell } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import type { DashboardNotification } from "../types/dashboardSummary";

interface NotificationsCardProps {
  notifications: DashboardNotification[];
}

function formatRelative(value: string) {
  const minutes = Math.floor((Date.now() - new Date(value).getTime()) / 60000);
  if (minutes < 1) return "방금 전";
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}일 전`;
  return new Intl.DateTimeFormat("ko-KR", { month: "2-digit", day: "2-digit" }).format(new Date(value));
}

/**
 * 최근 알림 카드(PRODUCT_STRUCTURE 대시보드 항목). notification(F 소유)은 읽기 전용으로
 * 최근 3건만 보여주고, 읽음 처리 등 원본 변경은 F의 알림 기능에 맡긴다.
 */
export function NotificationsCard({ notifications }: NotificationsCardProps) {
  return (
    <Card className="border border-slate-200 bg-white">
      <CardHeader className="pb-3">
        <CardTitle className="text-base flex items-center gap-2">
          <Bell className="size-4 text-blue-600" />
          최근 알림
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {notifications.length > 0 ? (
          notifications.map((notification) => {
            const body = (
              <div className="flex items-start gap-2.5 rounded-lg p-2 transition-colors hover:bg-slate-50">
                <span
                  className={`mt-1.5 size-2 shrink-0 rounded-full ${notification.read ? "bg-slate-200" : "bg-blue-500"}`}
                  aria-label={notification.read ? "읽음" : "안 읽음"}
                />
                <span className="min-w-0">
                  <span className={`block truncate text-sm ${notification.read ? "text-slate-500" : "font-semibold text-slate-800"}`}>
                    {notification.title}
                  </span>
                  <span className="mt-0.5 block text-xs text-slate-400">{formatRelative(notification.createdAt)}</span>
                </span>
              </div>
            );
            return notification.link ? (
              <Link key={notification.id} to={notification.link} className="block">
                {body}
              </Link>
            ) : (
              <div key={notification.id}>{body}</div>
            );
          })
        ) : (
          <p className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
            아직 알림이 없습니다. 분석 완료, 면접 리포트 등 주요 소식이 이곳에 표시됩니다.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
