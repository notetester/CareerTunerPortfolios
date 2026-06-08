import { create } from "zustand";
import * as supportApi from "../api/supportApi";
import type { Faq, Notice, SupportTicket } from "../types/support";

interface SupportState {
  /* FAQ */
  faqs: Faq[];
  faqLoading: boolean;
  fetchFaqs: (category?: string) => Promise<void>;

  /* 공지 */
  notices: Notice[];
  noticeLoading: boolean;
  currentNotice: Notice | null;
  fetchNotices: () => Promise<void>;
  fetchNoticeDetail: (id: number) => Promise<void>;

  /* 문의 */
  submitting: boolean;
  lastTicket: SupportTicket | null;
  createTicket: (data: {
    category: string;
    subject: string;
    content: string;
  }) => Promise<void>;
}

export const useSupportStore = create<SupportState>((set) => ({
  faqs: [],
  faqLoading: false,
  notices: [],
  noticeLoading: false,
  currentNotice: null,
  submitting: false,
  lastTicket: null,

  fetchFaqs: async (category) => {
    set({ faqLoading: true });
    const faqs = await supportApi.getFaqs(category);
    set({ faqs, faqLoading: false });
  },

  fetchNotices: async () => {
    set({ noticeLoading: true });
    const notices = await supportApi.getNotices();
    set({ notices, noticeLoading: false });
  },

  fetchNoticeDetail: async (id) => {
    const currentNotice = await supportApi.getNoticeDetail(id);
    set({ currentNotice: currentNotice ?? null });
  },

  createTicket: async (data) => {
    set({ submitting: true });
    const lastTicket = await supportApi.createTicket(data);
    set({ lastTicket, submitting: false });
  },
}));
