export type ChatbotRequestLane =
  | "restore"
  | "sessions"
  | "history"
  | "reply"
  | "interview-result"
  | "attachment"
  | "voice";

export interface ChatbotRequestSnapshot {
  lane: ChatbotRequestLane;
  controller: AbortController;
  accountKey: string;
  accountGeneration: number;
  conversationGeneration: number;
  conversationBound: boolean;
}

export function chatbotAccountKey(accountId: number | null | undefined): string;
export function interviewHandoffStorageKey(accountId: number | null | undefined): string | null;

export class ChatbotRequestScope {
  constructor(accountId: number | null | undefined);
  get accountKey(): string;
  switchAccount(accountId: number | null | undefined): boolean;
  invalidateConversation(): void;
  begin(
    lane: ChatbotRequestLane,
    options?: { conversationBound?: boolean },
  ): ChatbotRequestSnapshot;
  isCurrent(request: ChatbotRequestSnapshot): boolean;
  finish(request: ChatbotRequestSnapshot): void;
  cancelLane(lane: ChatbotRequestLane): void;
  abortAll(): void;
}
