import { api } from "@/app/lib/api";
import type { Inquiry, InquiryStatus, InquiryMessage } from "../data/inquiriesData";
import type {
  AdminTicketListResponse,
  AdminTicketDetailResponse,
} from "../types/adminTicket";

function toStatus(s: string): InquiryStatus {
  switch (s) {
    case "pending":  return "pending";
    case "progress": return "progress";
    case "answered": return "answered";
    default:         return "pending";
  }
}

function detailToInquiry(b: AdminTicketDetailResponse): Inquiry {
  const msgs: InquiryMessage[] = (b.msgs ?? [])
    .filter((m) => !m.internal)
    .map((m) => ({
      who: m.who,
      name: m.name,
      time: m.time,
      text: m.text,
    }));
  return {
    id: b.id,
    cat: b.category,
    title: b.subject,
    member: b.memberName,
    date: b.createdAt,
    status: toStatus(b.status),
    assignee: "미지정",
    priority: b.priority,
    plan: b.plan ?? "무료",
    joined: b.joinedAt ?? "",
    lastPay: "",
    memo: b.memo ?? "",
    msgs,
  };
}

function listToInquiry(b: AdminTicketListResponse): Inquiry {
  return {
    id: b.id,
    cat: b.category,
    title: b.subject,
    member: b.memberName,
    date: b.createdAt,
    status: toStatus(b.status),
    assignee: "미지정",
    priority: b.priority,
    plan: b.plan ?? "무료",
    joined: b.joinedAt ?? "",
    lastPay: "",
    memo: "",
    msgs: [],
  };
}

export function getTickets(status?: string): Promise<Inquiry[]> {
  const q = status ? `?status=${status}` : "";
  return api<AdminTicketListResponse[]>(`/admin/tickets${q}`).then((list) =>
    list.map(listToInquiry),
  );
}

export function getTicketDetail(id: number): Promise<Inquiry> {
  return api<AdminTicketDetailResponse>(`/admin/tickets/${id}`).then(
    detailToInquiry,
  );
}

export function updateTicket(
  id: number,
  patch: { status?: string; priority?: string },
): Promise<Inquiry> {
  return api<AdminTicketDetailResponse>(`/admin/tickets/${id}`, {
    method: "PATCH",
    body: JSON.stringify(patch),
  }).then(detailToInquiry);
}

export function reply(
  id: number,
  content: string,
  internal = false,
): Promise<Inquiry> {
  return api<AdminTicketDetailResponse>(`/admin/tickets/${id}/reply`, {
    method: "POST",
    body: JSON.stringify({ content, internal }),
  }).then(detailToInquiry);
}
