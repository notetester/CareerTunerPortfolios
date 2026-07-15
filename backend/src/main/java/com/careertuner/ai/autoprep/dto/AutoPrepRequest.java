package com.careertuner.ai.autoprep.dto;

import java.util.List;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/**
 * AI 오케스트레이터 실행 요청.
 * query: 한 줄 자연어("네이버 백엔드 신입 통째로 준비해줘"). applicationCaseId/mode 는 명시 시 슬롯 파싱을 덮어쓴다(선택).
 * attachmentFileIds: /api/file/upload 로 올린 <b>자소서</b> 첨부 파일 id(선택). WRITE(자소서 교정)가 소비한다.
 * jobPostingFileIds: /api/file/upload 로 올린 <b>공고(텍스트/PDF-텍스트/docx)</b> 첨부 파일 id(선택). 지원 건이 없을 때
 *   인테이크가 이 첨부의 본문을 뽑아 지원 건을 자동 생성한다. 이미지/스캔 공고는 프론트가 지원 건을 먼저 만들어
 *   applicationCaseId 로 넘기므로 여기 담기지 않는다. 플랜별 개수 게이팅(무료 1개/유료 다수)은 오케가 적용한다.
 */
public record AutoPrepRequest(
    String query,
    Long applicationCaseId,
    String mode,
    String coverLetterText,
    List<Long> attachmentFileIds,
    @Size(max = 1, message = "공고 첨부는 한 번에 1개만 선택할 수 있습니다.")
    List<Long> jobPostingFileIds,
    @Size(max = 80)
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "runId 형식이 올바르지 않습니다.")
    String runId
) {
    /** 공고 첨부 미사용 호출 호환(jobPostingFileIds=null). 기존 5-arg 호출부는 수정 없이 컴파일된다. */
    public AutoPrepRequest(String query, Long applicationCaseId, String mode,
                           String coverLetterText, List<Long> attachmentFileIds) {
        this(query, applicationCaseId, mode, coverLetterText, attachmentFileIds, null, null);
    }

    /** runId 도입 전 6-arg 호출 호환. */
    public AutoPrepRequest(String query, Long applicationCaseId, String mode,
                           String coverLetterText, List<Long> attachmentFileIds,
                           List<Long> jobPostingFileIds) {
        this(query, applicationCaseId, mode, coverLetterText, attachmentFileIds, jobPostingFileIds, null);
    }
}
