package com.careertuner.ai.autoprep;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.careertuner.common.text.DocumentTextExtractor;
import com.careertuner.common.text.DocumentTextExtractor.Extraction;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.service.FileService;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 첨부 파일 로더. 요청의 attachmentFileIds 를 플랜 한도 내에서 불러오고, 텍스트형(text/*, 텍스트 PDF, .docx)은 본문을 추출한다.
 *
 * <p>플랜 게이팅: 무료(FREE/BASIC) 1개, 유료(PRO/PREMIUM) 5개. (요금제 세부 정책은 E와 추후 조정 — TODO)
 * 한도 초과분은 버리고 로그만 남긴다. 개별 파일 로드 실패도 건너뛰어 항상 진행한다.
 * 텍스트 추출은 {@link DocumentTextExtractor} 에 위임하고, 절단({@link #MAX_TEXT_CHARS})은 로더에 남긴다.
 * 추출 실패(스캔 PDF 등)는 throw 하지 않고 text=null 로 유지한다(항상 진행 — AutoPrep 원칙).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoPrepAttachmentLoader {

    private static final int FREE_LIMIT = 1;
    private static final int PAID_LIMIT = 5;
    static final int MAX_TEXT_CHARS = 12000;

    private final UserMapper userMapper;
    private final FileService fileService;
    private final DocumentTextExtractor documentTextExtractor;

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
        // 추출 대상 형식만 시도 — 그 외(이미지 등)는 text=null 유지(기존 동작)
        if (isExtractable(contentType, asset.getOriginalName())) {
            Extraction extraction = documentTextExtractor.extract(
                    download.bytes(), contentType, asset.getOriginalName());
            if (extraction.isSuccess()) {
                text = truncate(extraction.text());
            } else {
                log.debug("AutoPrep 첨부 텍스트 추출 실패 fileId={} reason={}",
                        asset.getId(), extraction.reason());
            }
        }
        return new PrepAttachment(asset.getId(), asset.getOriginalName(), contentType, asset.getSizeBytes(), text);
    }

    /** text/* · markdown · pdf · docx 만 공용 추출기에 넘긴다. 구형 .doc 은 미지원(text=null). */
    private static boolean isExtractable(String contentType, String originalName) {
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.startsWith("text/") || ct.contains("markdown")
                    || ct.contains("pdf") || ct.contains("wordprocessingml")) {
                return true;
            }
        }
        if (originalName == null) {
            return false;
        }
        String lower = originalName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")
                || lower.endsWith(".pdf") || lower.endsWith(".docx");
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }
}
