import { create } from "zustand";
import * as supportApi from "../api/supportApi";
import type { Faq, Notice, SupportTicket } from "../types/support";

interface SupportState {
  /* FAQ */
  faqs: Faq[];
  faqLoading: boolean;
  faqError: string | null;
  fetchFaqs: (category?: string) => Promise<void>;

  /* 공지 */
  notices: Notice[];
  noticeLoading: boolean;
  noticeError: string | null;
  currentNotice: Notice | null;
  fetchNotices: () => Promise<void>;
  fetchNoticeDetail: (id: number) => Promise<void>;

  /* 문의 */
  submitting: boolean;
  submitError: string | null;
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
  faqError: null,
  notices: [],
  noticeLoading: false,
  noticeError: null,
  currentNotice: null,
  submitting: false,
  submitError: null,
  lastTicket: null,

  fetchFaqs: async (category) => {
    set({ faqLoading: true, faqError: null });
    try {
      const faqs = await supportApi.getFaqs(category);
      set({ faqs, faqLoading: false });
    } catch (e) {
      set({ faqLoading: false, faqError: (e as Error).message });
    }
  },

  fetchNotices: async () => {
    set({ noticeLoading: true, noticeError: null });
    try {
      const notices = await supportApi.getNotices();
      set({ notices, noticeLoading: false });
    } catch (e) {
      set({ noticeLoading: false, noticeError: (e as Error).message });
    }
  },

  fetchNoticeDetail: async (id) => {
    try {
      const currentNotice = await supportApi.getNoticeDetail(id);
      set({ currentNotice: currentNotice ?? null });
    } catch (e) {
      set({ noticeError: (e as Error).message });
    }
  },

  createTicket: async (data) => {
    set({ submitting: true, submitError: null });
    try {
      const lastTicket = await supportApi.createTicket(data);
      set({ lastTicket, submitting: false });
    } catch (e) {
      set({ submitting: false, submitError: (e as Error).message });
      throw e;
    }
  },
}));
