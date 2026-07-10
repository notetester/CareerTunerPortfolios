package com.careertuner.profile.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.careertuner.profile.domain.UserProfile;

public enum JobFamily {
    DEVELOPMENT_DATA("개발/데이터", "개발, 데이터, 인프라, QA 직무", List.of(
            "개발", "프론트", "백엔드", "데이터", "분석", "서버", "앱", "웹", "qa", "java", "spring", "react",
            "python", "sql")),
    SALES_MARKETING("영업/마케팅", "영업, 마케팅, 브랜딩, 광고, 고객 성장 직무", List.of(
            "영업", "마케팅", "브랜드", "브랜딩", "광고", "세일즈", "고객", "crm", "콘텐츠", "sns", "ae")),
    DESIGN_CONTENT("디자인/콘텐츠", "디자인, 영상, 콘텐츠, UX/UI 직무", List.of(
            "디자인", "콘텐츠", "영상", "편집", "ux", "ui", "figma", "photoshop", "일러스트", "브랜딩")),
    BUSINESS_OFFICE("경영/사무", "기획, 인사, 회계, 총무, 재무, 운영 직무", List.of(
            "기획", "인사", "회계", "재무", "총무", "사무", "운영", "전략", "관리", "엑셀", "excel")),
    HEALTHCARE_SERVICE("의료/서비스", "의료, 간호, 상담, 고객 서비스 직무", List.of(
            "간호", "의료", "병원", "보건", "상담", "서비스", "고객응대", "CS", "요양", "치료",
            "원무", "접수", "보험청구", "emr", "환자", "병원코디네이터")),
    EDUCATION_PUBLIC("교육/공공", "교육, 강의, 행정, 공공기관 직무", List.of(
            "교육", "강의", "교사", "교직", "공공", "행정", "공무", "훈련", "멘토링")),
    PRODUCTION_LOGISTICS("생산/물류", "생산, 품질, 물류, 구매, 현장 운영 직무", List.of(
            "생산", "품질", "물류", "구매", "제조", "공정", "창고", "재고", "안전", "현장")),
    ENGINEERING_TECHNICAL("기술/공학", "기계, 전기전자, 건설, 설비, 화학, 바이오, 환경안전, 연구개발 직무", List.of(
            "기계", "기계설계", "전기", "전자", "전기전자", "건설", "토목", "건축", "설비", "화학",
            "바이오", "환경", "환경안전", "산업안전", "연구개발", "r&d", "cad", "도면", "plc",
            "회로", "실험", "시험", "검증", "소재", "공학")),
    GENERAL("공통 직무", "직무군이 명확하지 않을 때 쓰는 기본 평가 기준", List.of());

    private final String label;
    private final String description;
    private final List<String> keywords;

    JobFamily(String label, String description, List<String> keywords) {
        this.label = label;
        this.description = description;
        this.keywords = keywords;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public List<String> keywords() {
        return keywords;
    }

    public static JobFamily classify(UserProfile profile) {
        String text = searchableText(profile);
        return Arrays.stream(values())
                .filter(family -> family != GENERAL)
                .max((left, right) -> Integer.compare(left.keywordScore(text), right.keywordScore(text)))
                .filter(family -> family.keywordScore(text) > 0)
                .orElse(GENERAL);
    }

    private int keywordScore(String text) {
        int score = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                score++;
            }
        }
        return score;
    }

    private static String searchableText(UserProfile profile) {
        if (profile == null) {
            return "";
        }
        return String.join(" ",
                value(profile.getDesiredJob()),
                value(profile.getDesiredIndustry()),
                value(profile.getSkills()),
                value(profile.getCareer()),
                value(profile.getProjects()),
                value(profile.getPortfolioEvidence()),
                value(profile.getResumeText()),
                value(profile.getSelfIntro()))
                .toLowerCase(Locale.ROOT);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
