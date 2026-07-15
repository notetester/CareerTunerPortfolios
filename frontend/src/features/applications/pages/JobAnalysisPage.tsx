import { NewApplicationPage } from "./NewApplicationPage";

/**
 * Footer의 공고 분석 진입점.
 *
 * 공고 등록·추출·검수·분석은 지원 건 생성 파이프라인이 단일 진실원이므로 그 기능을 재사용하되,
 * 사용자가 "지원 건 목록"이 아닌 "공고 분석" 작업으로 진입했음을 제목과 안내에서 분명히 한다.
 */
export function JobAnalysisPage() {
  return (
    <NewApplicationPage
      pageTitle="공고 분석"
      pageDescription="채용공고를 등록하고 추출 내용을 검수한 뒤 직무 요구사항과 기업 분석 결과를 확인합니다. 분석 기록은 지원 건에 안전하게 연결됩니다."
      loginTitle="공고 분석은 로그인 후 사용할 수 있습니다"
      loginDescription="분석할 공고와 결과는 사용자별 지원 건에 저장됩니다."
      backHref="/applications"
      backLabel="지원 건 관리"
    />
  );
}
