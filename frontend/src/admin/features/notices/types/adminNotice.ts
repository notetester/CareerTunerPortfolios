/** 백엔드 /api/admin/notices 응답 1:1 매핑 */
export interface AdminNoticeResponse {
  id: number;
  title: string;
  content: string;
  category: string | null;
  status: string;
  pinned: boolean;
  thumbnailUrl: string | null;
  viewCount: number;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
