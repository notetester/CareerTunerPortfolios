package com.careertuner.legal.service;

import java.time.LocalDateTime;
import java.util.List;

import com.careertuner.legal.domain.LegalDocType;
import com.careertuner.legal.dto.LegalDocResponse;

/** 관리자 게시본이 아직 없는 개발·초기 배포 환경에서 보여줄 최소 공개 정책. */
final class DefaultLegalDocumentCatalog {

    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 7, 10, 0, 0);

    private DefaultLegalDocumentCatalog() {
    }

    static LegalDocResponse response(LegalDocType type) {
        return new LegalDocResponse(
                type.dbValue(),
                type.label(),
                "v2026.07",
                EFFECTIVE_AT,
                EFFECTIVE_AT,
                summary(type),
                clauses(type));
    }

    private static String summary(LegalDocType type) {
        return switch (type) {
            case TERMS -> "CareerTuner 공개 베타의 계정, 지원 건과 AI 보조 기능 이용 조건입니다.";
            case PRIVACY -> "회원 식별과 지원 준비 기능에 필요한 개인정보 처리 범위와 사용자 권리입니다.";
            case MARKETING -> "신규 기능과 취업 준비 콘텐츠 안내를 받기 위한 선택 동의입니다.";
            case AI_CONSENT -> "사용자가 요청한 AI 분석·면접·첨삭을 위한 데이터 처리 동의입니다.";
            case RESUME_CONSENT -> "이력서 파일과 프로필 원문을 구조화·분석하기 위한 별도 선택 동의입니다.";
            case COPYRIGHT -> "사용자 원본 자료와 CareerTuner 콘텐츠의 권리와 이용 범위입니다.";
        };
    }

    private static List<LegalDocResponse.ClauseDto> clauses(LegalDocType type) {
        return switch (type) {
            case TERMS -> List.of(
                    clause(1, "목적과 적용", "CareerTuner는 지원 건 중심 취업 준비 도구입니다. 현재 공개본은 포트폴리오·공개 베타이며 취업 결과를 보장하지 않습니다."),
                    clause(2, "계정과 사용자 책임", "사용자는 본인의 정보만 등록하고 계정 접근 수단을 안전하게 관리해야 합니다. 타인의 이력서나 자기소개서를 허락 없이 등록할 수 없습니다."),
                    clause(3, "AI 결과의 성격", "AI 분석, 면접 질문과 첨삭 결과는 참고 정보입니다. 사용자는 사실관계와 최종 제출 문서를 직접 검토해야 합니다."),
                    clause(4, "동의 철회", "필수 이용약관 동의를 철회하면 다시 동의할 때까지 회원 기능 이용이 중단됩니다. 설정, 법적 문서와 고객센터는 계속 이용할 수 있습니다."));
            case PRIVACY -> List.of(
                    clause(1, "처리 항목", "계정 식별 정보, 프로필의 학력·경력·프로젝트·기술, 지원 건과 서비스 이용 이력을 필요한 범위에서 처리합니다."),
                    clause(2, "처리 목적", "회원 식별, 계정 보안, 지원 건 관리, 사용자가 요청한 기능 제공과 고객문의 처리를 위해 이용합니다."),
                    clause(3, "보관과 파기", "처리 목적 달성 또는 회원 탈퇴 후 법적·보안상 필요한 기간을 제외하고 삭제하거나 비식별화합니다."),
                    clause(4, "사용자 권리", "사용자는 설정과 고객센터에서 열람, 정정, 삭제, 처리 제한과 동의 철회를 요청할 수 있습니다. 필수 동의 철회 시 회원 기능이 중단됩니다."));
            case MARKETING -> List.of(
                    clause(1, "수신 항목", "신규 기능, 공개 행사, 취업 준비 콘텐츠, 이벤트와 혜택 안내를 이메일 또는 서비스 알림으로 받을 수 있습니다."),
                    clause(2, "선택 동의", "마케팅 수신은 선택 사항이며 동의하지 않아도 기본 서비스 이용에 영향이 없습니다."),
                    clause(3, "철회", "설정의 개인정보 관리에서 언제든지 철회할 수 있으며, 철회 이후 새 마케팅 안내를 발송하지 않습니다."));
            case AI_CONSENT -> List.of(
                    clause(1, "처리 목적", "공고 분석, 스펙 비교, 지원 전략, 면접 평가와 문서 첨삭처럼 사용자가 요청한 AI 결과를 생성하기 위해 입력 데이터를 처리합니다."),
                    clause(2, "처리 항목", "프로필과 지원 건 맥락, 공고 원문, 자기소개서, 면접 답변, 기능 실행 상태와 사용량을 처리할 수 있습니다."),
                    clause(3, "결과와 보관", "사용자의 원본을 공개 모델 학습 데이터로 판매하지 않습니다. 결과는 자동 생성된 참고 정보이며 사용자가 검토해야 합니다."),
                    clause(4, "철회", "설정에서 철회하면 AI 분석, 면접 평가, 첨삭과 자동 준비 기능이 즉시 중단되며 다시 동의한 뒤 재개됩니다."));
            case RESUME_CONSENT -> List.of(
                    clause(1, "수집·이용 항목", "이력서와 자기소개서에 포함된 학력, 경력, 프로젝트, 기술, 자격과 사용자가 작성한 프로필 원문을 처리할 수 있습니다."),
                    clause(2, "처리 목적", "파일 텍스트 추출, 프로필 구조화, 직무 역량 요약·기술 추출·완성도 진단을 제공하기 위해 이용합니다."),
                    clause(3, "선택 동의", "동의하지 않아도 계정과 수동 프로필 편집은 사용할 수 있지만 이력서 가져오기와 이력서 기반 분석은 사용할 수 없습니다."),
                    clause(4, "철회", "설정에서 철회하면 새로운 이력서 가져오기와 분석이 중단되며 다시 동의하기 전까지 해당 기능을 실행할 수 없습니다."));
            case COPYRIGHT -> List.of(
                    clause(1, "권리의 귀속", "사용자가 업로드한 이력서, 자기소개서와 포트폴리오 원본의 권리는 사용자에게 있습니다."),
                    clause(2, "이용 범위", "사용자는 자신의 취업 준비를 위해 생성 결과를 저장·출력하고 개인적으로 공유할 수 있습니다. 서비스 전체의 무단 복제와 대량 수집은 금지됩니다."),
                    clause(3, "침해 신고", "타인의 저작권, 개인정보 또는 초상권 침해를 발견하면 고객센터에 대상 위치와 권리 근거를 제출할 수 있습니다."));
        };
    }

    private static LegalDocResponse.ClauseDto clause(int seq, String title, String body) {
        return new LegalDocResponse.ClauseDto(seq, title, body);
    }
}
