import { useEffect } from "react";
import { useChatbot } from "../hooks/useChatbot";
import { ChatbotPanel } from "./ChatbotWidget";

/**
 * 전용 AI 상담 화면도 플로팅 위젯과 동일한 패널을 사용한다.
 * 기능을 두 벌로 구현하면 온보딩·AutoPrep·오류/음성/모델 선택이 한쪽에서 누락되므로,
 * 표시 방식만 embedded로 바꾸고 모든 상태 전이는 하나의 구현을 공유한다.
 */
export function ChatbotFullScreen() {
  const chatbot = useChatbot();

  useEffect(() => {
    chatbot.restoreRecent();
    chatbot.loadSessions();
  }, [chatbot.restoreRecent, chatbot.loadSessions]);

  return <ChatbotPanel chatbot={chatbot} embedded />;
}
