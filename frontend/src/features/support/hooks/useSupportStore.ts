import { create } from "zustand";
// TODO: 백엔드 연동 시 주석 해제
// import * as supportApi from "../api/supportApi";
import { mockFaqs, mockNotices } from "../data/mockSupport";
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
    // TODO: 백엔드 연동 시 supportApi.getFaqs(category) 로 교체
    const filtered = category
      ? mockFaqs.filter((f) => f.category === category)
      : mockFaqs;
    set({ faqs: filtered, faqLoading: false });
  },

  fetchNotices: async () => {
    set({ noticeLoading: true, noticeError: null });
    // TODO: 백엔드 연동 시 supportApi.getNotices() 로 교체
    set({ notices: mockNotices, noticeLoading: false });
  },

  fetchNoticeDetail: async (id) => {
    // TODO: 백엔드 연동 시 supportApi.getNoticeDetail(id) 로 교체
    const currentNotice = mockNotices.find((n) => n.id === id) ?? null;
    set({ currentNotice });
  },

  createTicket: async (data) => {
    set({ submitting: true, submitError: null });
    // TODO: 백엔드 연동 시 supportApi.createTicket(data) 로 교체
    const lastTicket: SupportTicket = {
      id: Date.now(),
      subject: data.subject,
      content: data.content,
      status: "RECEIVED",
      createdAt: new Date().toISOString(),
    };
    set({ lastTicket, submitting: false });
  },
}));
