import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router";
import { ArrowRight, Globe2, Inbox, RefreshCw, UserPlus, Users } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import {
  discoverConversations,
  listConversations,
  listFriends,
  listIncomingFriendRequests,
} from "../api/collaborationApi";

type MessengerSummary = {
  rooms: number | null;
  discoverable: number | null;
  friends: number | null;
  incoming: number | null;
};

const EMPTY_SUMMARY: MessengerSummary = {
  rooms: null,
  discoverable: null,
  friends: null,
  incoming: null,
};

export function MessengerOverviewPage() {
  const [summary, setSummary] = useState<MessengerSummary>(EMPTY_SUMMARY);
  const [loading, setLoading] = useState(true);
  const [warning, setWarning] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setWarning(null);
    const results = await Promise.allSettled([
      listConversations(),
      discoverConversations("", 30),
      listFriends(),
      listIncomingFriendRequests(),
    ]);
    const [rooms, discoverable, friends, incoming] = results;
    setSummary({
      rooms: rooms.status === "fulfilled" ? rooms.value.length : null,
      discoverable: discoverable.status === "fulfilled" ? discoverable.value.length : null,
      friends: friends.status === "fulfilled" ? friends.value.length : null,
      incoming: incoming.status === "fulfilled" ? incoming.value.length : null,
    });
    const failed = results.filter((result) => result.status === "rejected").length;
    if (failed > 0) {
      setWarning(
        failed === results.length
          ? "메신저 서버에 연결할 수 없습니다. 서버 상태를 확인한 뒤 다시 시도해 주세요."
          : "일부 메신저 요약을 불러오지 못했습니다. 각 기능 페이지는 계속 열 수 있습니다.",
      );
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const cards = [
    {
      title: "내 채팅방",
      description: "참여 중인 방을 열어 메시지, 파일, 지원 공고를 공유합니다.",
      href: "/messenger/rooms",
      icon: Inbox,
      metric: summary.rooms,
      metricLabel: "참여 중",
    },
    {
      title: "공개방 찾기",
      description: "주제별 공개방을 검색하고 비공개방에는 비밀번호로 참가합니다.",
      href: "/messenger/discover",
      icon: Globe2,
      metric: summary.discoverable,
      metricLabel: "참가 가능",
    },
    {
      title: "친구 관리",
      description: "사용자를 검색하고 받은 요청을 처리하거나 1:1 대화를 시작합니다.",
      href: "/messenger/friends",
      icon: Users,
      metric: summary.friends,
      metricLabel: "친구",
      secondary: summary.incoming,
    },
  ];

  return (
    <main className="min-h-[calc(100vh-8rem)] bg-muted/30 pb-24">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6 lg:px-8">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <div className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">메신저</div>
            <h1 className="mt-1 text-3xl font-black tracking-tight text-foreground">대화와 사람을 한눈에 관리하세요</h1>
            <p className="mt-2 text-sm text-muted-foreground">채팅방, 공개방 탐색, 친구 관리는 서로 독립된 화면에서 필요한 데이터만 불러옵니다.</p>
          </div>
          <Button type="button" variant="outline" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
            요약 새로고침
          </Button>
        </div>

        {warning && (
          <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-700 dark:text-amber-300">
            {warning}
          </div>
        )}

        <section className="grid gap-4 lg:grid-cols-3" aria-label="메신저 기능">
          {cards.map((card) => (
            <Link
              key={card.href}
              to={card.href}
              className="group flex min-h-56 flex-col rounded-2xl border border-border bg-card p-6 shadow-sm transition hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md"
            >
              <div className="flex items-start justify-between gap-4">
                <span className="flex size-12 items-center justify-center rounded-xl bg-primary/10 text-primary">
                  <card.icon className="size-6" />
                </span>
                <div className="text-right">
                  <div className="text-2xl font-black text-foreground">{card.metric ?? (loading ? "…" : "-")}</div>
                  <div className="text-xs text-muted-foreground">{card.metricLabel}</div>
                  {card.secondary != null && (
                    <div className="mt-1 inline-flex items-center gap-1 text-xs font-semibold text-primary">
                      <UserPlus className="size-3.5" /> 받은 요청 {card.secondary}
                    </div>
                  )}
                </div>
              </div>
              <h2 className="mt-6 text-xl font-bold text-foreground">{card.title}</h2>
              <p className="mt-2 flex-1 text-sm leading-6 text-muted-foreground">{card.description}</p>
              <span className="mt-5 inline-flex items-center gap-1 text-sm font-semibold text-primary">
                기능 열기 <ArrowRight className="size-4 transition-transform group-hover:translate-x-1" />
              </span>
            </Link>
          ))}
        </section>
      </div>
    </main>
  );
}
