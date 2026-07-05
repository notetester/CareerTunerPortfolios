package com.careertuner.ai.autoprep;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.service.FileService;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 첨부 파일 로더. 요청의 attachmentFileIds 를 플랜 한도 내에서 불러오고, 텍스트형은 본문을 추출한다.
 *
 * <p>플랜 게이팅: 무료(FREE/BASIC) 1개, 유료(PRO/PREMIUM) 5개. (요금제 세부 정책은 E와 추후 조정 — TODO)
 * 한도 초과분은 버리고 로그만 남긴다. 개별 파일 로드 실패도 건너뛰어 항상 진행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoPrepAttachmentLoader {

    private static final int FREE_LIMIT = 1;
    private static final int PAID_LIMIT = 5;
    private static final int MAX_TEXT_CHARS = 12000;

    private final UserMapper userMapper;
    private final FileService fileService;

    public List<PrepAttachment> load(Long userId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        int limit = attachmentLimit(userId);
        List<PrepAttachment> result = new ArrayList<>();
        for (Long fileId : fileIds) {
            if (fileId == null) {
                continue;
            }
            if (result.size() >= limit) {
                log.info("AutoPrep 첨부 한도 초과 — 플랜 한도 {}개까지만 사용(userId={})", limit, userId);
                break;
            }
            try {
                FileService.Download download = fileService.download(userId, fileId);
                result.add(toAttachment(download));
            } catch (RuntimeException ex) {
                log.warn("AutoPrep 첨부 로드 실패 fileId={}: {}", fileId, ex.getMessage());
            }
        }
        return result;
    }

    private int attachmentLimit(Long userId) {
        User user = userMapper.findById(userId);
        String plan = (user == null || user.getPlan() == null) ? "FREE" : user.getPlan().trim().toUpperCase();
        return switch (plan) {
            case "PRO", "PREMIUM" -> PAID_LIMIT;
            default -> FREE_LIMIT;
        };
    }

    private PrepAttachment toAttachment(FileService.Download download) {
        FileAsset asset = download.asset();
        String contentType = asset.getContentType();
        String text = null;
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text")) {
            text = truncate(new String(download.bytes(), StandardCharsets.UTF_8));
        } else if (isPdf(contentType, asset.getOriginalName())) {
            text = truncate(extractPdfText(download.bytes(), asset.getId()));
        }
        return new PrepAttachment(asset.getId(), asset.getOriginalName(), contentType, asset.getSizeBytes(), text);
    }

    /** contentType 이 pdf 이거나(브라우저별 편차 대비) 확장자가 .pdf 면 PDF 로 본다. */
    private static boolean isPdf(String contentType, String originalName) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf")) {
            return true;
        }
        return originalName != null && originalName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /**
     * 텍스트 PDF 본문 추출 — B 의 {@code JobPostingTextExtractor.extractTextPdf} 와 동일 패턴(PDFBox).
     * 스캔/이미지 PDF 는 추출 결과가 비어 null 을 돌려준다(Vision OCR 은 별도 작업 — MASTER_PLAN §6).
     * 추출 실패해도 throw 하지 않는다 — 첨부 자체(메타)는 유지하고 text 만 비운다(항상 진행 원칙).
     */
    private String extractPdfText(byte[] bytes, Long fileId) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document).trim();
            return text.isEmpty() ? null : text;
        } catch (IOException ex) {
            log.warn("AutoPrep 첨부 PDF 텍스트 추출 실패 fileId={}: {}", fileId, ex.getMessage());
            return null;
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }
}
