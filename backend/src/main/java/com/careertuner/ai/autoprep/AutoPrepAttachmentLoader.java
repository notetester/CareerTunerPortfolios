package com.careertuner.ai.autoprep;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.careertuner.common.text.DocumentTextExtractor;
import com.careertuner.common.text.DocumentTextExtractor.Extraction;
import com.careertuner.billing.domain.SubscriptionBenefitPolicy;
import com.careertuner.billing.service.BillingPolicyService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.service.FileService;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 첨부 파일 로더. 요청의 attachmentFileIds 를 플랜 한도 내에서 불러오고, 텍스트형(text/*, 텍스트 PDF, .docx)은 본문을 추출한다.
 *
 * <p>플랜 게이팅은 E billing policy의 {@code AUTOPREP_ATTACHMENT} 수량을 정본으로 사용한다.
 * API 진입 시 공고+자소서 distinct 총량 초과를 거절하고, 로더에서도 허용 범위를 한 번 더 제한한다.
 * 개별 파일 로드 실패는 건너뛰어 항상 진행한다.
 * 텍스트 추출은 {@link DocumentTextExtractor} 에 위임하고, 절단({@link #MAX_TEXT_CHARS})은 로더에 남긴다.
 * 추출 실패(스캔 PDF 등)는 throw 하지 않고 text=null 로 유지한다(항상 진행 — AutoPrep 원칙).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoPrepAttachmentLoader {

    static final String ATTACHMENT_BENEFIT_CODE = "AUTOPREP_ATTACHMENT";
    private static final int SAFE_FALLBACK_LIMIT = 1;
    private static final int MAX_POLICY_LIMIT = 20;
    static final int MAX_TEXT_CHARS = 12000;

    private final UserMapper userMapper;
    private final BillingPolicyService billingPolicyService;
    private final FileService fileService;
    private final DocumentTextExtractor documentTextExtractor;

    public List<PrepAttachment> load(Long userId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        int limit = attachmentLimit(userId);
        return loadSelected(userId, fileIds, limit);
    }

    /** 공고와 자소서 첨부의 요청 단위 distinct 총량을 플랜 정책과 대조하고 초과 요청은 400으로 거절한다. */
    public void validateRequestLimit(Long userId,
                                     List<Long> jobPostingFileIds,
                                     List<Long> attachmentFileIds) {
        validateRequestLimit(userId, jobPostingFileIds, attachmentFileIds, 0);
    }

    /**
     * 아직 file_asset ID가 아닌 multipart 공고도 같은 요청의 1개 첨부로 합산한다. 이 검사는 지원 건 생성보다
     * 먼저 실행돼 무료 플랜에서 PDF/이미지 공고 + 자소서를 조합해 한도를 우회하지 못하게 한다.
     */
    public void validateRequestLimit(Long userId,
                                     List<Long> jobPostingFileIds,
                                     List<Long> attachmentFileIds,
                                     int externalAttachmentCount) {
        LinkedHashSet<Long> requested = new LinkedHashSet<>();
        addValidIds(requested, jobPostingFileIds);
        addValidIds(requested, attachmentFileIds);
        int limit = attachmentLimit(userId);
        int total = requested.size() + Math.max(0, externalAttachmentCount);
        if (total > limit) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "AutoPrep 첨부는 공고와 자소서를 합해 최대 %d개까지 사용할 수 있습니다.".formatted(limit));
        }
    }

    /** binary 공고 멱등키로 받은 pending fileId가 요청 사용자 본인의 AutoPrep 대기 파일인지 확인한다. */
    public void validatePendingAutoPrepFile(Long userId, Long fileId) {
        FileAsset asset = fileService.download(userId, fileId).asset();
        if (!Objects.equals("ATTACHMENT", asset.getKind())
                || !Objects.equals("AUTO_PREP_PENDING", asset.getRefType())
                || asset.getRefId() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "AutoPrep 전송 대기 중인 본인 파일을 확인할 수 없습니다.");
        }
    }

    /**
     * 한 요청의 공고+자소서 첨부를 합산해 플랜 한도를 적용한 뒤, 현재 단계가 소비할 파일만 읽는다.
     * 공고를 먼저 배정하여 인테이크와 실행 API가 각각 로더를 호출해도 같은 요청에서 한도를 두 번 받지 않는다.
     */
    public List<PrepAttachment> loadForRequest(Long userId,
                                               List<Long> selectedFileIds,
                                               List<Long> jobPostingFileIds,
                                               List<Long> attachmentFileIds) {
        if (selectedFileIds == null || selectedFileIds.isEmpty()) {
            return List.of();
        }
        int limit = attachmentLimit(userId);
        LinkedHashSet<Long> orderedRequestIds = new LinkedHashSet<>();
        addValidIds(orderedRequestIds, jobPostingFileIds);
        addValidIds(orderedRequestIds, attachmentFileIds);

        Set<Long> allowed = orderedRequestIds.stream()
                .limit(limit)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (orderedRequestIds.size() > limit) {
            log.info("AutoPrep 요청 첨부 총량 초과 — 공고+자소서 합산 {}개 중 플랜 한도 {}개만 사용(userId={})",
                    orderedRequestIds.size(), limit, userId);
        }
        List<Long> selectedAllowed = selectedFileIds.stream()
                .filter(id -> id != null && allowed.contains(id))
                .distinct()
                .toList();
        return loadSelected(userId, selectedAllowed, selectedAllowed.size());
    }

    private List<PrepAttachment> loadSelected(Long userId, List<Long> fileIds, int limit) {
        List<PrepAttachment> result = new ArrayList<>();
        for (Long fileId : fileIds.stream().distinct().toList()) {
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

    private static void addValidIds(Set<Long> target, List<Long> fileIds) {
        if (fileIds != null) {
            fileIds.stream().filter(id -> id != null).forEach(target::add);
        }
    }

    private int attachmentLimit(Long userId) {
        User user = userMapper.findById(userId);
        String plan = (user == null || user.getPlan() == null) ? "FREE" : user.getPlan().trim().toUpperCase();
        SubscriptionBenefitPolicy policy = billingPolicyService.activeBenefitPolicy(
                plan, ATTACHMENT_BENEFIT_CODE, null);
        if (policy == null || policy.getQuantity() < 0) {
            log.warn("AutoPrep 첨부 정책 누락/오류 — 안전 기본값 {}개 적용(plan={})",
                    SAFE_FALLBACK_LIMIT, plan);
            return SAFE_FALLBACK_LIMIT;
        }
        return Math.min(policy.getQuantity(), MAX_POLICY_LIMIT);
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
