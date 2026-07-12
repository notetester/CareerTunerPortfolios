import { ChatbotFullScreen } from "../components/ChatbotFullScreen";

export function ChatbotPage() {
  return (
    <div className="w-full min-w-0 max-w-[1180px] mx-auto px-4 sm:px-6 py-5 sm:py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-extrabold tracking-tight">AI 상담</h1>
        <p className="text-sm text-muted-foreground mt-1">
          FAQ·공지·가이드 문서를 검색해 근거와 함께 답변드려요. 찾지 못하면 상담사를 연결해 드립니다.
        </p>
      </div>
      <ChatbotFullScreen />
    </div>
  );
}
