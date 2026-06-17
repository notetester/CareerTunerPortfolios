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

  /* 내 문의 내역 */
  myTickets: SupportTicket[];
  ticketsLoading: boolean;
  ticketsError: string | null;
  fetchMyTickets: () => Promise<void>;
}

export const useSupportStore = create<SupportState>((set, get) => ({
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
  myTickets: [],
  ticketsLoading: false,
  ticketsError: null,

  fetchFaqs: async (category) => {
    set({ faqLoading: true, faqError: null });
    try {
      const faqs = await supportApi.getFaqs(category);
      set({ faqs, faqLoading: false });
    } catch (error) {
      set({
        faqs: [],
        faqLoading: false,
        faqError: error instanceof Error ? error.message : "FAQ를 불러오지 못했습니다.",
      });
    }
  },

  fetchNotices: async () => {
    set({ noticeLoading: true, noticeError: null });
    try {
      const notices = await supportApi.getNotices();
      set({ notices, noticeLoading: false });
    } catch (error) {
      set({
        notices: [],
        noticeLoading: false,
        noticeError: error instanceof Error ? error.message : "공지사항을 불러오지 못했습니다.",
      });
    }
  },

  fetchNoticeDetail: async (id) => {
    set({ noticeLoading: true, noticeError: null, currentNotice: null });
    try {
      const currentNotice = await supportApi.getNoticeDetail(id);
      set({ currentNotice: currentNotice ?? null, noticeLoading: false });
    } catch (error) {
      set({
        currentNotice: null,
        noticeLoading: false,
        noticeError: error instanceof Error ? error.message : "공지사항을 불러오지 못했습니다.",
      });
    }
  },

  createTicket: async (data) => {
    set({ submitting: true, submitError: null });
    try {
      const lastTicket = await supportApi.createTicket(data);
      set({ lastTicket, submitting: false });
      // 접수 직후 내 문의 내역을 갱신해 새 문의가 바로 보이게 한다.
      void get().fetchMyTickets();
    } catch (error) {
      set({
        lastTicket: null,
        submitting: false,
        submitError: error instanceof Error ? error.message : "문의를 접수하지 못했습니다.",
      });
      throw error;
    }
  },

  fetchMyTickets: async () => {
    set({ ticketsLoading: true, ticketsError: null });
    try {
      const myTickets = await supportApi.getMyTickets();
      set({ myTickets, ticketsLoading: false });
    } catch (error) {
      set({
        myTickets: [],
        ticketsLoading: false,
        ticketsError: error instanceof Error ? error.message : "문의 내역을 불러오지 못했습니다.",
      });
    }
  },
}));
