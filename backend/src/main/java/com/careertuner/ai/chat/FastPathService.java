package com.careertuner.ai.chat;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.careertuner.ai.chat.ChatResponse.SiteLink;

/**
 * Fast-path: "커뮤니티 어디?", "로그인" 같은 순수 내비 질의는 LLM·검색을 둘 다 우회하고 즉답한다.
 * <p>오발(검색/대화 질의를 가로채는 것)이 더 나쁘므로 <b>보수적으로</b> 발동한다:
 * 짧은 질의 + 목적지 키워드 + (내비 단서 또는 키워드 단독) + 검색/내용 의도어 없음일 때만.
 * 여기서 만드는 링크는 서버가 직접 만든 신뢰 링크라 컨트롤러의 화이트리스트 검증을 거치지 않는다.
 */
@Service
public class FastPathService {

    private static final int MAX_LEN = 20;

    /** 검색/대화 의도가 섞이면 fast-path 금지 (에이전트로). */
    private static final List<String> EXCLUSIONS = List.of(
            "후기", "추천", "찾", "작성", "어떻게", "방법", "왜", "비교", "알려", "설명", "차이", "어떤");

    /** 내비 단서 (목적지 키워드와 함께 있으면 이동 의도로 본다). */
    private static final List<String> NAV_CUES = List.of(
            "어디", "가기", "가고", "이동", "페이지", "바로", "열어", "보여", "보기", "가줘", "가는", "메뉴", "링크");

    private record Dest(List<String> keywords, String path, String label, String message) {}

    private static final List<Dest> DESTS = List.of(
            new Dest(List.of("커뮤니티", "게시판"), "/community", "커뮤니티 바로가기", "커뮤니티는 여기서 보실 수 있어요."),
            new Dest(List.of("로그인"), "/login", "로그인", "로그인 페이지로 이동하실 수 있어요."),
            new Dest(List.of("요금", "가격", "구독", "플랜"), "/pricing", "요금제", "요금제 안내는 여기서 확인하세요."),
            new Dest(List.of("공지"), "/support/notices", "공지사항", "공지사항은 여기서 확인하실 수 있어요."),
            new Dest(List.of("faq", "자주"), "/support/faq", "자주 묻는 질문", "FAQ는 여기서 보실 수 있어요."),
            new Dest(List.of("문의", "1:1", "상담사", "고객센터"), "/support/contact", "1:1 문의", "1:1 문의는 여기서 남기실 수 있어요."),
            new Dest(List.of("모의면접", "ai면접", "면접연습"), "/interview", "AI 면접", "AI 모의면접은 여기서 진행하실 수 있어요."),
            new Dest(List.of("알림"), "/notifications", "알림", "알림은 여기서 확인하실 수 있어요."),
            new Dest(List.of("마이페이지", "프로필", "내정보"), "/profile", "프로필", "프로필은 여기서 보실 수 있어요."),
            new Dest(List.of("설정"), "/settings", "설정", "설정은 여기서 변경하실 수 있어요."),
            new Dest(List.of("지원서", "지원목록", "공고"), "/applications", "지원 관리", "지원 관리는 여기서 확인하실 수 있어요."),
            new Dest(List.of("대시보드"), "/dashboard", "대시보드", "대시보드는 여기서 보실 수 있어요."));

    /**
     * 내비 질의면 즉답 ChatResponse, 아니면 empty(에이전트로).
     */
    public Optional<ChatResponse> tryFastPath(String question) {
        if (question == null) {
            return Optional.empty();
        }
        String q = question.trim();
        if (q.isEmpty() || q.length() > MAX_LEN) {
            return Optional.empty();
        }
        String norm = q.toLowerCase().replace(" ", "");
        if (EXCLUSIONS.stream().anyMatch(norm::contains)) {
            return Optional.empty();
        }

        for (Dest d : DESTS) {
            for (String kw : d.keywords()) {
                if (!norm.contains(kw)) {
                    continue;
                }
                boolean keywordOnly = norm.length() <= kw.length() + 2; // 키워드 ± 조사 수준
                boolean hasNavCue = NAV_CUES.stream().anyMatch(norm::contains);
                if (keywordOnly || hasNavCue) {
                    return Optional.of(new ChatResponse(
                            d.message(),
                            List.of(new SiteLink(d.label(), d.path())),
                            List.of()));
                }
            }
        }
        return Optional.empty();
    }
}
