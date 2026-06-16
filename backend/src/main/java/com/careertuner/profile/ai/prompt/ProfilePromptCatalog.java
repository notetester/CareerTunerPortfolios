package com.careertuner.profile.ai.prompt;

import com.careertuner.admin.prompt.dto.AdminPromptView;

public final class ProfilePromptCatalog {

    private ProfilePromptCatalog() {
    }

    public static AdminPromptView view() {
        return new AdminPromptView(
                "profile",
                "A 프로필 AI 요약/직무 역량 추출/완성도 진단",
                "v1",
                "사용자의 이력서, 경력, 활동, 프로젝트, 자기소개서, 직무 역량을 근거로 요약과 보강 제안을 생성한다.",
                "특정 직군에 치우치지 말고 사무, 영업, 마케팅, 디자인, 의료, 교육, 생산, 개발 등 다양한 직무 맥락을 반영한다. 확정되지 않은 AI 추출값은 사용자 확인 전 저장하지 않는다. 부족한 항목은 구체적인 입력 제안으로 반환한다. AI 데이터 사용 동의가 없거나 철회된 사용자는 실행하지 않는다.",
                "{ summary, extractedSkills[], strengths[], gaps[], recommendations[], completenessScore }"
        );
    }
}
