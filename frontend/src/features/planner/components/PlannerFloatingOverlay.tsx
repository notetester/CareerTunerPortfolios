import { useEffect, useMemo, useRef, useState } from "react";
import type { PointerEvent } from "react";
import { Link } from "react-router";
import { CalendarClock, GripHorizontal, Layers, MousePointerClick, Pin, StickyNote, X } from "lucide-react";
import { haptic } from "@/platform/haptics";
import { notifyPlannerReminderNative, stopPlannerResident, syncPlannerResident } from "@/platform/plannerNative";
import { toast } from "@/features/notification/components/toast";
import { getPlannerDashboard } from "../api/plannerApi";
import type { PlannerDashboard, PlannerScheduleItem, PlannerScheduleReminder } from "../types/planner";
import "../styles/planner.css";

const OVERLAY_ENABLED_KEY = "ct.planner.overlay.enabled";
const OVERLAY_POSITION_KEY = "ct.planner.overlay.position";
const FIRED_REMINDERS_KEY = "ct.planner.reminders.fired";

interface OverlayPosition {
  x: number;
  y: number;
}

const OVERLAY_WIDTH = 340;

/** 바(닫기 버튼 포함)가 항상 화면 안에 남도록 위치를 클램프한다 — 드래그로 X 를 화면 밖에 못 밀어낸다. */
function clampPosition(position: OverlayPosition): OverlayPosition {
  const viewportWidth = typeof window === "undefined" ? 1280 : window.innerWidth;
  const viewportHeight = typeof window === "undefined" ? 800 : window.innerHeight;
  return {
    x: Math.max(8, Math.min(viewportWidth - OVERLAY_WIDTH - 8, position.x)),
    y: Math.max(8, Math.min(viewportHeight - 52, position.y)),
  };
}

export function PlannerFloatingOverlay({ enabled }: { enabled: boolean }) {
  const [data, setData] = useState<PlannerDashboard | null>(null);
  const [visible, setVisible] = useState(() => readOverlayEnabled());
  const [collapsed, setCollapsed] = useState(false);
  const [position, setPosition] = useState<OverlayPosition>(() => clampPosition(readPosition()));
  const dragRef = useRef<{ dx: number; dy: number } | null>(null);
  const positionRef = useRef(position);

  const overlayItems = useMemo(() => {
    // 고정(pinned) 항목을 위로 — "핀 고정"에 실제 정렬 의미를 준다.
    const pinnedFirst = <T extends { pinned: boolean }>(list: T[]): T[] =>
      list.slice().sort((left, right) => Number(right.pinned) - Number(left.pinned));
    const memos = pinnedFirst(data?.memos.filter((memo) => memo.overlayVisible) ?? []);
    const schedules = pinnedFirst(
      data?.scheduleItems.filter((item) => item.overlayVisible && item.status !== "CANCELED") ?? [],
    );
    return { memos, schedules };
  }, [data]);

  const hasItems = overlayItems.memos.length + overlayItems.schedules.length > 0;

  useEffect(() => {
    if (!enabled || !visible) return;
    let ignore = false;
    const load = async () => {
      try {
        const now = new Date();
        const from = new Date(now);
        from.setDate(from.getDate() - 1);
        const to = new Date(now);
        to.setDate(to.getDate() + 30);
        const result = await getPlannerDashboard(toApiDateTime(from), toApiDateTime(to));
        if (!ignore) setData(result);
      } catch {
        if (!ignore) setData(null);
      }
    };
    void load();
    const timer = window.setInterval(load, 60_000);
    return () => {
      ignore = true;
      window.clearInterval(timer);
    };
  }, [enabled, visible]);

  useEffect(() => {
    if (!data) return;
    fireDueReminders(data.scheduleItems);
  }, [data]);

  useEffect(() => {
    positionRef.current = position;
  }, [position]);

  // 창 크기가 줄어도 바가 화면 밖으로 나가지 않게 재클램프.
  useEffect(() => {
    const onResize = () => setPosition((current) => clampPosition(current));
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  useEffect(() => {
    void syncPlannerResident(enabled && visible, data);
  }, [data, enabled, visible]);

  useEffect(() => {
    return () => {
      void stopPlannerResident();
    };
  }, []);

  if (!enabled) return null;

  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    dragRef.current = { dx: event.clientX - position.x, dy: event.clientY - position.y };
    event.currentTarget.setPointerCapture(event.pointerId);
  };

  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (!dragRef.current) return;
    const next = clampPosition({
      x: event.clientX - dragRef.current.dx,
      y: event.clientY - dragRef.current.dy,
    });
    positionRef.current = next;
    setPosition(next);
  };

  const onPointerUp = () => {
    dragRef.current = null;
    writePosition(positionRef.current);
  };

  const hide = () => {
    setVisible(false);
    writeOverlayEnabled(false);
  };

  if (!visible) {
    return (
      <button
        className="ct-planner-fab"
        onClick={() => {
          setVisible(true);
          writeOverlayEnabled(true);
        }}
        aria-label="플래너 오버레이 열기"
      >
        <Layers className="size-5" />
      </button>
    );
  }

  return (
    <aside
      className="ct-planner-overlay"
      style={{ left: position.x, top: position.y }}
      aria-label="플래너 오버레이"
    >
      <div className="ct-planner-overlay__bar">
        <div
          className="ct-planner-overlay__grip"
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          role="button"
          aria-label="드래그해서 이동"
          title="드래그해서 이동"
        >
          <GripHorizontal className="size-4" />
        </div>
        <button type="button" onClick={() => setCollapsed((value) => !value)} className="ct-planner-overlay__title">
          플래너
        </button>
        <Link to="/planner" className="ct-planner-overlay__link">열기</Link>
        <button type="button" onClick={hide} className="ct-planner-overlay__icon" aria-label="오버레이 닫기">
          <X className="size-4" />
        </button>
      </div>
      {!collapsed && (
        <div className="ct-planner-overlay__body">
          {!hasItems && <div className="ct-planner-overlay__empty">표시 중인 메모나 일정이 없습니다.</div>}
          {overlayItems.schedules.slice(0, 5).map((item) => (
            <div
              key={`schedule-${item.id}`}
              className={`ct-planner-overlay__item ${item.clickThrough ? "ct-planner-overlay__item--pass" : ""} ${item.pinned ? "ct-planner-overlay__item--pinned" : ""}`}
              style={{ opacity: item.opacity }}
            >
              <div className="ct-planner-overlay__meta">
                <CalendarClock className="size-3.5" />
                {timeLabel(item)}
                <ItemFlags pinned={item.pinned} clickThrough={item.clickThrough} opacity={item.opacity} />
              </div>
              <div className="ct-planner-overlay__item-title">{item.title}</div>
              {item.applicationCompanyName && <div className="ct-planner-overlay__sub">{item.applicationCompanyName} · {item.applicationJobTitle}</div>}
            </div>
          ))}
          {overlayItems.memos.slice(0, 5).map((memo) => (
            <div key={`memo-${memo.id}`} className={`ct-planner-overlay__memo ct-planner-overlay__memo--${memo.color} ${memo.pinned ? "ct-planner-overlay__memo--pinned" : ""}`} style={{ opacity: memo.opacity }}>
              <div className="ct-planner-overlay__meta">
                <StickyNote className="size-3.5" />
                <ItemFlags pinned={memo.pinned} opacity={memo.opacity} />
              </div>
              <div className="ct-planner-overlay__item-title">{memo.title || "메모"}</div>
              {memo.content && <div className="ct-planner-overlay__sub">{memo.content}</div>}
            </div>
          ))}
        </div>
      )}
    </aside>
  );
}

/** 항목별로 적용된 설정(고정/클릭 통과/투명도)을 배지로 — 어느 항목에 무엇이 걸렸는지 한눈에. */
function ItemFlags({ pinned, clickThrough, opacity }: { pinned?: boolean; clickThrough?: boolean; opacity: number }) {
  const dimmed = opacity < 0.999;
  if (!pinned && !clickThrough && !dimmed) return null;
  return (
    <div className="ct-planner-overlay__flags">
      {pinned && (
        <span className="ct-planner-overlay__flag ct-planner-overlay__flag--pin">
          <Pin className="size-3" />
          고정
        </span>
      )}
      {clickThrough && (
        <span className="ct-planner-overlay__flag ct-planner-overlay__flag--pass">
          <MousePointerClick className="size-3" />
          클릭 통과
        </span>
      )}
      {dimmed && <span className="ct-planner-overlay__flag">{Math.round(opacity * 100)}%</span>}
    </div>
  );
}

function fireDueReminders(items: PlannerScheduleItem[]) {
  const fired = readFiredReminderIds();
  const now = Date.now();
  const nextFired = new Set(fired);
  items.forEach((item) => {
    item.reminders
      .filter((reminder) => reminder.status === "PENDING")
      .filter((reminder) => reminder.remindAt && new Date(reminder.remindAt).getTime() <= now)
      .filter((reminder) => !fired.has(reminder.id))
      .forEach((reminder) => {
        nextFired.add(reminder.id);
        toast.notify({
          type: "SCHEDULE_REMINDER",
          category: "notice",
          title: item.title,
          message: timeLabel(item),
          link: "/planner",
        });
        if (reminder.channels.includes("BROWSER")) showBrowserNotification(item, reminder);
        notifyPlannerReminderNative(item, reminder, timeLabel(item));
        if (reminder.soundEnabled) playBeep();
        if (reminder.vibrationEnabled) haptic("medium");
      });
  });
  if (nextFired.size !== fired.size) writeFiredReminderIds(nextFired);
}

function showBrowserNotification(item: PlannerScheduleItem, reminder: PlannerScheduleReminder) {
  if (typeof window === "undefined" || !("Notification" in window)) return;
  // 권한 요청은 설정/온보딩에서 사용자가 명시적으로 푸시를 켤 때만 수행한다.
  if (window.Notification.permission !== "granted") return;
  const show = () => {
    try {
      new window.Notification(`일정 알림: ${item.title}`, {
        body: timeLabel(item),
        tag: `planner-${reminder.id}`,
      });
    } catch {
      // 브라우저 알림은 보조 수단이다.
    }
  };
  show();
}

function playBeep() {
  try {
    const AudioContextCtor = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioContextCtor) return;
    const context = new AudioContextCtor();
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.type = "sine";
    oscillator.frequency.value = 880;
    gain.gain.value = 0.04;
    oscillator.connect(gain);
    gain.connect(context.destination);
    oscillator.start();
    oscillator.stop(context.currentTime + 0.18);
  } catch {
    // 소리 알림은 보조 수단이다.
  }
}

function readOverlayEnabled() {
  try {
    return localStorage.getItem(OVERLAY_ENABLED_KEY) === "true";
  } catch {
    return false;
  }
}

function writeOverlayEnabled(value: boolean) {
  try {
    localStorage.setItem(OVERLAY_ENABLED_KEY, String(value));
  } catch {
    // no-op
  }
}

function readPosition(): OverlayPosition {
  try {
    const raw = localStorage.getItem(OVERLAY_POSITION_KEY);
    if (!raw) return { x: 24, y: 120 };
    const parsed = JSON.parse(raw) as Partial<OverlayPosition>;
    return { x: Number(parsed.x ?? 24), y: Number(parsed.y ?? 120) };
  } catch {
    return { x: 24, y: 120 };
  }
}

function writePosition(position: OverlayPosition) {
  try {
    localStorage.setItem(OVERLAY_POSITION_KEY, JSON.stringify(position));
  } catch {
    // no-op
  }
}

function readFiredReminderIds(): Set<number> {
  try {
    const parsed = JSON.parse(localStorage.getItem(FIRED_REMINDERS_KEY) ?? "[]") as number[];
    return new Set(parsed.filter((id) => Number.isFinite(id)));
  } catch {
    return new Set();
  }
}

function writeFiredReminderIds(ids: Set<number>) {
  try {
    localStorage.setItem(FIRED_REMINDERS_KEY, JSON.stringify(Array.from(ids).slice(-200)));
  } catch {
    // no-op
  }
}

function timeLabel(item: PlannerScheduleItem) {
  if (item.allDay || item.timingPrecision === "DAY") return "하루종일";
  const start = new Date(item.startAt).toLocaleString("ko-KR", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
  if (!item.endAt) return start;
  const end = new Date(item.endAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
  return `${start} - ${end}`;
}

function toApiDateTime(date: Date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function pad(value: number) {
  return String(value).padStart(2, "0");
}
