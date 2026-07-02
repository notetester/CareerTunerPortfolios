/**
 * 자체 마운트 Toast 시스템
 *
 * Provider/App.tsx 수정 없이 어디서든 사용 가능:
 *   import { toast } from '@/features/notification/components/toast';
 *   toast.success("저장되었습니다");
 *   toast.notify({ type: "COMMENT", category: "community", title: "..." });
 */
import { createRoot, type Root } from "react-dom/client";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import {
  FileSearch,
  ClipboardList,
  Building2,
  Target,
  TrendingUp,
  HelpCircle,
  FileText,
  PenLine,
  BookOpen,
  MessageCircle,
  Reply,
  Heart,
  EyeOff,
  Megaphone,
  MessageSquareReply,
  CreditCard,
  ShieldAlert,
  Ticket,
  UserPlus,
  AlertTriangle,
  FileEdit,
  Trash2,
  RotateCcw,
  type LucideIcon,
} from "lucide-react";
import type {
  NotificationType,
  NotificationCategory,
} from "../types/notification";
import "../styles/toast.css";

/* ─── Types ─── */

type ToastVariant = "info" | "success" | "warning" | "danger" | "error";
type ToastSurface = "light" | "filled" | "dark";

interface StatusItem {
  id: number;
  kind: "status";
  message: ReactNode;
  variant: ToastVariant;
  surface: ToastSurface;
  duration: number;
  showProgress: boolean;
}

interface LoadingItem {
  id: number;
  kind: "loading";
  message: ReactNode;
}

interface NotifyItem {
  id: number;
  kind: "notify";
  notiType: NotificationType;
  category: NotificationCategory;
  title: string;
  message?: string;
  link?: string;
  actorName?: string;
  duration: number;
  showProgress: boolean;
}

type ToastItem = StatusItem | LoadingItem | NotifyItem;

/* ─── 모듈 상태 (싱글턴) ─── */

type Listener = () => void;
let items: ToastItem[] = [];
let nextId = 0;
const listeners = new Set<Listener>();

function emit() {
  listeners.forEach((fn) => fn());
}

function subscribe(fn: Listener) {
  listeners.add(fn);
  // useEffect 클린업은 void 반환이어야 한다. Set.delete 의 boolean 반환을 흘려보내지 않도록 블록으로 감싼다.
  return () => {
    listeners.delete(fn);
  };
}

function dismiss(id: number) {
  items = items.filter((t) => t.id !== id);
  emit();
}

/* ─── 외부 API ─── */

interface NotifyInput {
  type: NotificationType;
  category: NotificationCategory;
  title: string;
  message?: string;
  link?: string;
  actorName?: string;
  duration?: number;
  showProgress?: boolean;
}

function pushStatus(
  message: ReactNode,
  variant: ToastVariant = "info",
  opts?: { surface?: ToastSurface; duration?: number; showProgress?: boolean }
): number {
  const id = ++nextId;
  items = [
    ...items,
    {
      id,
      kind: "status",
      message,
      variant,
      surface: opts?.surface ?? "light",
      duration: opts?.duration ?? 4000,
      showProgress: opts?.showProgress ?? true,
    },
  ];
  emit();
  ensureMounted();
  return id;
}

function pushNotify(input: NotifyInput): number {
  const id = ++nextId;
  items = [
    ...items,
    {
      id,
      kind: "notify",
      notiType: input.type,
      category: input.category,
      title: input.title,
      message: input.message,
      link: input.link,
      actorName: input.actorName,
      duration: input.duration ?? 5000,
      showProgress: input.showProgress ?? true,
    },
  ];
  emit();
  ensureMounted();
  return id;
}

function pushLoading(message: ReactNode): number {
  const id = ++nextId;
  items = [
    ...items,
    { id, kind: "loading", message },
  ];
  emit();
  ensureMounted();
  return id;
}

export const toast = {
  success: (msg: ReactNode, opts?: { duration?: number }) =>
    pushStatus(msg, "success", opts),
  error: (msg: ReactNode, opts?: { duration?: number }) =>
    pushStatus(msg, "error", opts),
  warning: (msg: ReactNode, opts?: { duration?: number }) =>
    pushStatus(msg, "warning", opts),
  info: (msg: ReactNode, opts?: { duration?: number }) =>
    pushStatus(msg, "info", opts),
  loading: (msg: ReactNode) => pushLoading(msg),
  notify: (input: NotifyInput) => pushNotify(input),
  dismiss,
  clear: () => {
    items = [];
    emit();
  },
};

/* ─── Notification type → icon/color ─── */

const NOTI_ICON_MAP: Record<NotificationType, LucideIcon> = {
  PROFILE_ANALYZED: FileSearch,
  JOB_ANALYSIS_COMPLETE: ClipboardList,
  COMPANY_ANALYSIS_COMPLETE: Building2,
  FIT_ANALYSIS_COMPLETE: Target,
  CAREER_TREND_COMPLETE: TrendingUp,
  JOB_POSTING_EXTRACTION_SUCCEEDED: FileSearch,
  JOB_POSTING_EXTRACTION_REVIEW_REQUIRED: AlertTriangle,
  JOB_POSTING_EXTRACTION_FAILED: AlertTriangle,
  QUESTIONS_GENERATED: HelpCircle,
  INTERVIEW_REPORT_READY: FileText,
  CORRECTION_COMPLETE: PenLine,
  COMMENT: MessageCircle,
  COMMENT_REPLY: Reply,
  COMMENT_HIDDEN: EyeOff,
  COMMENT_RESTORED: RotateCcw,
  COMMENT_REMOVED: Trash2,
  LIKE: Heart,
  POST_HIDDEN: EyeOff,
  POST_REMOVED: Trash2,
  POST_RESTORED: RotateCcw,
  POST_SUMMARY_READY: BookOpen,
  FRIEND_REQUEST: UserPlus,
  FRIEND_ACCEPTED: UserPlus,
  ROOM_INVITE: MessageCircle,
  ROOM_MESSAGE: MessageCircle,
  ROOM_MENTION: MessageSquareReply,
  RECOMMENDED_JOB: ClipboardList,
  MARKETING_AD: Megaphone,
  NOTICE: Megaphone,
  TICKET_ANSWERED: MessageSquareReply,
  ACCOUNT_BLOCKED: ShieldAlert,
  CREDIT_LOW: CreditCard,
  PAYMENT_COMPLETE: CreditCard,
  PAYMENT_SCHEDULED: CreditCard,
  SUBSCRIPTION_CANCELED: CreditCard,
  CREDIT_RECHARGED: CreditCard,
  NEW_REPORT: ShieldAlert,
  NEW_TICKET: Ticket,
  NEW_USER: UserPlus,
  LOW_CONFIDENCE_REPORT: AlertTriangle,
  TICKET_DRAFT_READY: FileEdit,
};

const CATEGORY_STYLE: Record<NotificationCategory, { bg: string; fg: string }> =
  {
    all: { bg: "var(--muted)", fg: "var(--muted-foreground)" },
    ai_analysis: { bg: "var(--cat-job-bg)", fg: "var(--cat-job-fg)" },
    interview: {
      bg: "var(--cat-interview-bg)",
      fg: "var(--cat-interview-fg)",
    },
    correction: { bg: "var(--cat-cert-bg)", fg: "var(--cat-cert-fg)" },
    community: { bg: "var(--cat-free-bg)", fg: "var(--cat-free-fg)" },
    messenger: { bg: "var(--cat-interview-bg)", fg: "var(--cat-interview-fg)" },
    recommendation: { bg: "var(--cat-job-bg)", fg: "var(--cat-job-fg)" },
    billing: { bg: "var(--cat-role-bg)", fg: "var(--cat-role-fg)" },
    notice: {
      bg: "var(--cat-portfolio-bg)",
      fg: "var(--cat-portfolio-fg)",
    },
    marketing: { bg: "var(--cat-pass-bg)", fg: "var(--cat-pass-fg)" },
    admin: { bg: "var(--cat-pass-bg)", fg: "var(--cat-pass-fg)" },
  };

/* ─── 자동 소멸 훅 ─── */
/*
 * showProgress=true → 진행 바 CSS 애니메이션이 끝나면 onAnimationEnd로 닫힘 (setTimeout 안 씀)
 * showProgress=false → setTimeout fallback
 * hover 시 일시정지는 둘 다 동일
 */

function useAutoClose(
  duration: number,
  onClose: () => void,
  showProgress: boolean
) {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startedAt = useRef(0);
  const remaining = useRef(duration);
  const [paused, setPaused] = useState(false);

  const clear = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  const arm = useCallback(
    (ms: number) => {
      clear();
      if (!duration || duration <= 0) return;
      startedAt.current = Date.now();
      // 진행 바가 있으면 onAnimationEnd가 닫기를 담당 → setTimeout 불필요
      if (!showProgress) {
        timerRef.current = setTimeout(onClose, ms);
      }
    },
    [clear, duration, onClose, showProgress]
  );

  useEffect(() => {
    remaining.current = duration;
    arm(duration);
    return clear;
  }, [duration, arm, clear]);

  const handleEnter = () => {
    if (!duration || duration <= 0) return;
    clear();
    remaining.current = Math.max(
      remaining.current - (Date.now() - startedAt.current),
      0
    );
    setPaused(true);
  };

  const handleLeave = () => {
    if (!duration || duration <= 0) return;
    setPaused(false);
    arm(remaining.current);
  };

  // 진행 바 애니메이션 종료 시 호출
  const handleProgressEnd = useCallback(() => {
    if (showProgress) onClose();
  }, [showProgress, onClose]);

  return { paused, handleEnter, handleLeave, handleProgressEnd };
}

/* ─── Status Glyph ─── */

function StatusGlyph({ variant }: { variant: string }) {
  const bg = "var(--toast-badge-bg)";
  const fg = "var(--toast-badge-fg)";

  if (variant === "warning") {
    return (
      <svg viewBox="0 0 24 24" fill="none">
        <path
          d="M12 3.4 22.1 20a1.2 1.2 0 0 1-1 1.8H2.9a1.2 1.2 0 0 1-1-1.8L12 3.4Z"
          fill={bg}
          strokeLinejoin="round"
        />
        <path
          d="M12 9.8v4.4"
          stroke={fg}
          strokeWidth="2"
          strokeLinecap="round"
        />
        <circle cx="12" cy="17.7" r="1.25" fill={fg} />
      </svg>
    );
  }

  const glyph =
    variant === "success" ? (
      <path
        d="M7.4 12.2l3 3 6.2-6.6"
        stroke={fg}
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    ) : variant === "info" ? (
      <>
        <circle cx="12" cy="7.9" r="1.3" fill={fg} />
        <path
          d="M12 11.2v5.4"
          stroke={fg}
          strokeWidth="2"
          strokeLinecap="round"
        />
      </>
    ) : (
      <>
        <path
          d="M12 6.8v5.8"
          stroke={fg}
          strokeWidth="2"
          strokeLinecap="round"
        />
        <circle cx="12" cy="16.5" r="1.3" fill={fg} />
      </>
    );

  return (
    <svg viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="10" fill={bg} />
      {glyph}
    </svg>
  );
}

/* ─── 닫기 버튼 ─── */

function CloseBtn({ onClick }: { onClick: () => void }) {
  return (
    <button
      type="button"
      className="ct-toast__close"
      aria-label="닫기"
      onClick={onClick}
    >
      <svg viewBox="0 0 20 20" fill="none">
        <path
          d="M5.5 5.5l9 9M14.5 5.5l-9 9"
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinecap="round"
        />
      </svg>
    </button>
  );
}

/* ─── 진행 바 ─── */

function ProgressBar({
  show,
  duration,
  paused,
  brand,
  onAnimationEnd,
}: {
  show: boolean;
  duration: number;
  paused: boolean;
  brand?: boolean;
  onAnimationEnd?: () => void;
}) {
  if (!show) return null;
  const animated = duration > 0;
  return (
    <span
      className={[
        "ct-toast__progress",
        brand ? "ct-toast__progress--brand" : "",
        animated ? "ct-toast__progress--anim" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      style={
        animated
          ? {
              animationDuration: `${duration}ms`,
              animationPlayState: paused ? "paused" : "running",
            }
          : undefined
      }
      onAnimationEnd={onAnimationEnd}
    />
  );
}

/* ─── LoadingToast ─── */

function LoadingToast({
  item,
  onClose,
}: {
  item: LoadingItem;
  onClose: () => void;
}) {
  return (
    <div className="ct-toast ct-toast--loading" role="status" aria-live="polite">
      <span className="ct-toast__spinner" />
      <span className="ct-toast__label">{item.message}</span>
      <CloseBtn onClick={onClose} />
      <span className="ct-toast__progress ct-toast__progress--indeterminate" />
    </div>
  );
}

/* ─── StatusToast ─── */

function StatusToast({
  item,
  onClose,
}: {
  item: StatusItem;
  onClose: () => void;
}) {
  const v = item.variant === "error" ? "danger" : item.variant;
  const [exiting, setExiting] = useState(false);

  const handleClose = useCallback(() => {
    setExiting(true);
    setTimeout(onClose, 200);
  }, [onClose]);

  const { paused, handleEnter, handleLeave, handleProgressEnd } = useAutoClose(
    item.duration,
    handleClose,
    item.showProgress
  );

  return (
    <div
      className={[
        "ct-toast",
        `ct-toast--${v}`,
        `ct-toast--${item.surface}`,
        exiting ? "ct-toast--exiting" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      role="status"
      aria-live="polite"
      onMouseEnter={handleEnter}
      onMouseLeave={handleLeave}
    >
      <span className="ct-toast__icon">
        <StatusGlyph variant={v} />
      </span>
      <span className="ct-toast__label">{item.message}</span>
      <CloseBtn onClick={handleClose} />
      <ProgressBar
        show={item.showProgress}
        duration={item.duration}
        paused={paused}
        onAnimationEnd={handleProgressEnd}
      />
    </div>
  );
}

/* ─── NotifyToast ─── */

function NotifyToast({
  item,
  onClose,
}: {
  item: NotifyItem;
  onClose: () => void;
}) {
  const [exiting, setExiting] = useState(false);
  const Icon = NOTI_ICON_MAP[item.notiType];
  const style = CATEGORY_STYLE[item.category] ?? CATEGORY_STYLE.all;

  const handleClose = useCallback(() => {
    setExiting(true);
    setTimeout(onClose, 200);
  }, [onClose]);

  const handleClick = () => {
    if (item.link) {
      window.location.href = item.link;
    }
    handleClose();
  };

  const { paused, handleEnter, handleLeave, handleProgressEnd } = useAutoClose(
    item.duration,
    handleClose,
    item.showProgress
  );

  return (
    <div
      className={[
        "ct-toast ct-toast--notify ct-toast--light",
        exiting ? "ct-toast--exiting" : "",
      ]
        .filter(Boolean)
        .join(" ")}
      role="status"
      aria-live="polite"
      onMouseEnter={handleEnter}
      onMouseLeave={handleLeave}
    >
      <div
        className="ct-toast__noti-icon"
        style={{ background: style.bg, color: style.fg }}
      >
        {Icon && <Icon size={18} strokeWidth={1.85} />}
      </div>
      <div
        className="ct-toast__noti-body"
        onClick={handleClick}
        role={item.link ? "link" : undefined}
        style={item.link ? { cursor: "pointer" } : undefined}
      >
        <span className="ct-toast__noti-title">
          {item.actorName && (
            <strong className="ct-toast__noti-actor">{item.actorName}</strong>
          )}
          {item.title}
        </span>
        {item.message && (
          <span className="ct-toast__noti-msg">{item.message}</span>
        )}
      </div>
      <CloseBtn
        onClick={(e?: React.MouseEvent) => {
          e?.stopPropagation();
          handleClose();
        }}
      />
      <ProgressBar
        show={item.showProgress}
        duration={item.duration}
        paused={paused}
        brand
        onAnimationEnd={handleProgressEnd}
      />
    </div>
  );
}

/* ─── Viewport (자체 마운트) ─── */

function ToastViewport() {
  const [, forceRender] = useState(0);

  useEffect(() => {
    const unsub = subscribe(() => forceRender((n) => n + 1));
    return () => { unsub(); };
  }, []);

  return (
    <div
      className="ct-toast-vp ct-toast-vp--bottom-right"
      aria-live="polite"
      aria-relevant="additions"
    >
      {items.map((t) =>
        t.kind === "loading" ? (
          <LoadingToast key={t.id} item={t} onClose={() => dismiss(t.id)} />
        ) : t.kind === "notify" ? (
          <NotifyToast key={t.id} item={t} onClose={() => dismiss(t.id)} />
        ) : (
          <StatusToast key={t.id} item={t} onClose={() => dismiss(t.id)} />
        )
      )}
    </div>
  );
}

/* ─── 자동 마운트 (최초 toast 호출 시 DOM에 삽입) ─── */

let root: Root | null = null;

function ensureMounted() {
  if (root) return;
  const container = document.createElement("div");
  container.id = "ct-toast-root";
  document.body.appendChild(container);
  root = createRoot(container);
  root.render(<ToastViewport />);
}
