/** 백엔드 /api/admin/faq 응답 1:1 매핑 */
export interface AdminFaqResponse {
  id: number;
  category: string;
  question: string;
  answer: string;
  published: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}
