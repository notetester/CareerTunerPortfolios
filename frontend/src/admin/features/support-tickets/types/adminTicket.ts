/** 백엔드 /api/admin/tickets 응답 1:1 매핑 */

export interface AdminTicketAttachment {
  id: number;
  name: string;
  size: number;
  contentType?: string;
  contentUrl: string;
}

export interface AdminTicketMessage {
  id: number;
  who: "user" | "admin";
  name: string;
  time: string;
  text: string;
  internal: boolean;
  attachments?: AdminTicketAttachment[];
}

export interface AdminTicketListResponse {
  id: number;
  category: string;
  subject: string;
  memberName: string;
  createdAt: string;
  status: string;
  priority: boolean;
  plan: string;
  joinedAt: string;
}

export interface AdminTicketDetailResponse {
  id: number;
  category: string;
  subject: string;
  memberName: string;
  createdAt: string;
  status: string;
  priority: boolean;
  plan: string;
  joinedAt: string;
  memo: string;
  msgs: AdminTicketMessage[];
}

export interface AdminTicketDraftResponse {
  draft: string;
}
