package com.careertuner.ai.autoprep;

/**
 * 오케스트레이터에 첨부된 파일 1개. 텍스트형(text/*)이면 text 에 추출 내용이 담긴다(그 외는 null).
 * 향후 PDF/이미지 OCR 추출이 붙으면 text 가 채워지는 종류가 늘어난다.
 */
public record PrepAttachment(
    Long fileId,
    String name,
    String contentType,
    Long sizeBytes,
    String text
) {
    public boolean hasText() {
        return text != null && !text.isBlank();
    }
}
