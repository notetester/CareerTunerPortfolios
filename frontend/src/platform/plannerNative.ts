import { isNativeApp, nativePlugin, platformName } from "./capacitor";
import { registerPlugin } from "@capacitor/core";
import type { PlannerDashboard, PlannerScheduleItem, PlannerScheduleReminder } from "@/features/planner/types/planner";

interface PlannerNativePlugin {
  startPlannerResident: (options: ResidentOptions) => Promise<{ active?: boolean }>;
  updatePlannerResident: (options: ResidentOptions) => Promise<{ active?: boolean }>;
  stopPlannerResident: () => Promise<void>;
  notifyPlannerReminder: (options: ReminderOptions) => Promise<void>;
}

interface ResidentOptions {
  title: string;
  body: string;
}

interface ReminderOptions {
  title: string;
  body: string;
  soundEnabled: boolean;
  vibrationEnabled: boolean;
  notificationId: number;
}

let residentActive = false;
let lastResidentBody = "";
const registeredPlannerNative = registerPlugin<PlannerNativePlugin>("PlannerNative");

export async function syncPlannerResident(enabled: boolean, data: PlannerDashboard | null): Promise<void> {
  if (!isNativeApp() || platformName() !== "android") return;
  const plugin = plannerNativePlugin();
  if (!plugin) return;

  if (!enabled || !data) {
    if (residentActive) {
      residentActive = false;
      lastResidentBody = "";
      await plugin.stopPlannerResident().catch(() => {});
    }
    return;
  }

  const body = residentBody(data);
  if (!body) {
    if (residentActive) {
      residentActive = false;
      lastResidentBody = "";
      await plugin.stopPlannerResident().catch(() => {});
    }
    return;
  }

  const options = { title: "CareerTuner 플래너", body };
  if (!residentActive) {
    const result = await plugin.startPlannerResident(options).catch(() => null);
    residentActive = Boolean(result?.active ?? true);
    lastResidentBody = body;
    return;
  }
  if (lastResidentBody !== body) {
    lastResidentBody = body;
    await plugin.updatePlannerResident(options).catch(() => {});
  }
}

export async function stopPlannerResident(): Promise<void> {
  if (!isNativeApp() || platformName() !== "android") return;
  const plugin = plannerNativePlugin();
  if (!plugin || !residentActive) return;
  residentActive = false;
  lastResidentBody = "";
  await plugin.stopPlannerResident().catch(() => {});
}

export function notifyPlannerReminderNative(item: PlannerScheduleItem, reminder: PlannerScheduleReminder, body: string): void {
  if (!isNativeApp() || platformName() !== "android") return;
  const plugin = plannerNativePlugin();
  if (!plugin) return;
  const mobileSound = reminder.channels.includes("MOBILE_SOUND") || reminder.channels.includes("MOBILE_SOUND_VIBRATE");
  const mobileVibrate = reminder.channels.includes("MOBILE_VIBRATE") || reminder.channels.includes("MOBILE_SOUND_VIBRATE");
  if (!mobileSound && !mobileVibrate && !reminder.soundEnabled && !reminder.vibrationEnabled) return;

  void plugin.notifyPlannerReminder({
    title: `일정 알림: ${item.title}`,
    body,
    soundEnabled: reminder.soundEnabled || mobileSound,
    vibrationEnabled: reminder.vibrationEnabled || mobileVibrate,
    notificationId: Number.isFinite(reminder.id) ? reminder.id + 50_000 : Date.now() % 2_000_000_000,
  }).catch(() => {});
}

function plannerNativePlugin(): PlannerNativePlugin | undefined {
  return nativePlugin<PlannerNativePlugin>("PlannerNative") ?? registeredPlannerNative;
}

function residentBody(data: PlannerDashboard): string {
  const schedules = data.scheduleItems.filter((item) => item.overlayVisible && item.status !== "CANCELED");
  const memos = data.memos.filter((memo) => memo.overlayVisible);
  const scheduleLine = schedules.slice(0, 2).map((item) => `${timeLabel(item)} ${item.title}`).join(" · ");
  const memoLine = memos.slice(0, 2).map((memo) => memo.title || memo.content || "메모").join(" · ");
  if (scheduleLine && memoLine) return `${scheduleLine}\n${memoLine}`;
  return scheduleLine || memoLine;
}

function timeLabel(item: PlannerScheduleItem) {
  if (item.allDay || item.timingPrecision === "DAY") return "하루종일";
  const start = new Date(item.startAt).toLocaleString("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
  if (!item.endAt) return start;
  const end = new Date(item.endAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
  return `${start}-${end}`;
}
