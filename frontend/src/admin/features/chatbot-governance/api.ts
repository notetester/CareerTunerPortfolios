import { api } from "@/app/lib/api";

export interface ChatbotQuotaPolicy {
  id: number;
  enabled: boolean;
  dailyLimit: number;
  updatedBy: number | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface ChatbotConversationRow {
  conversationId: number;
  userId: number | null;
  messageCount: number | null;
  updatedAt: string;
}

export function getChatbotQuotaPolicy(): Promise<ChatbotQuotaPolicy> {
  return api<ChatbotQuotaPolicy>("/admin/chatbot/quota-policy");
}

export function updateChatbotQuotaPolicy(payload: {
  enabled?: boolean;
  dailyLimit?: number;
  reason?: string;
}): Promise<ChatbotQuotaPolicy> {
  return api<ChatbotQuotaPolicy>("/admin/chatbot/quota-policy", {
    method: "PATCH",
    body: JSON.stringify(payload),
  });
}

export function getChatbotConversations(params: { userId?: number; limit?: number }): Promise<ChatbotConversationRow[]> {
  const q = new URLSearchParams();
  if (params.userId != null) q.set("userId", String(params.userId));
  q.set("limit", String(params.limit ?? 200));
  return api<ChatbotConversationRow[]>(`/admin/chatbot/conversations?${q}`);
}

export function deleteChatbotConversation(conversationId: number): Promise<void> {
  return api<void>(`/admin/chatbot/conversations/${conversationId}`, { method: "DELETE" });
}
