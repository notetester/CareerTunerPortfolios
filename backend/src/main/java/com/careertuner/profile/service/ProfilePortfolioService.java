package com.careertuner.profile.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.text.DocumentTextExtractor;
import com.careertuner.common.text.DocumentTextExtractor.Extraction;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.dto.FileAssetResponse;
import com.careertuner.file.service.FileService;
import com.careertuner.profile.domain.UserProfile;
import com.careertuner.profile.mapper.ProfileMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 프로필에 연결된 포트폴리오 파일의 업로드·입양·조회와 AI 참고 텍스트를 담당한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfilePortfolioService {

    public static final String REF_TYPE = "USER_PROFILE_PORTFOLIO";
    private static final String FILE_KIND = "PORTFOLIO";
    private static final int MAX_FILES = 10;
    private static final int MAX_EVIDENCE_FILES = 5;
    private static final int MAX_EVIDENCE_CHARS = 12_000;

    private final ProfileMapper profileMapper;
    private final FileService fileService;
    private final DocumentTextExtractor documentTextExtractor;

    /** 업로드 시점부터 profile ref 를 함께 기록해 ref_id=null 고아 파일이 생기지 않게 한다. */
    @Transactional
    public FileAssetResponse upload(Long userId, MultipartFile file) {
        UserProfile profile = ensureProfile(userId);
        if (linkedAssets(userId, profile.getId()).size() >= MAX_FILES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "포트폴리오 파일은 최대 10개까지 연결할 수 있습니다.");
        }
        return fileService.upload(userId, FILE_KIND, REF_TYPE, profile.getId(), file);
    }

    /** 구형/중단된 클라이언트가 먼저 올린 미연결 PORTFOLIO 파일도 안전하게 현재 프로필로 입양한다. */
    @Transactional
    public List<FileAssetResponse> link(Long userId, List<Long> fileIds) {
        UserProfile profile = ensureProfile(userId);
        int remaining = Math.max(0, MAX_FILES - linkedAssets(userId, profile.getId()).size());
        if (remaining > 0) {
            fileService.linkOwnedFilesOfKind(userId, fileIds, FILE_KIND, REF_TYPE, profile.getId(), remaining);
        }
        return list(userId);
    }

    public List<FileAssetResponse> list(Long userId) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile == null || profile.getId() == null) {
            return List.of();
        }
        return linkedAssets(userId, profile.getId()).stream()
                .map(FileAssetResponse::from)
                .toList();
    }

    /** 현재 사용자 프로필에 실제로 연결된 포트폴리오만 삭제한다. */
    @Transactional
    public void delete(Long userId, Long fileId) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile == null || profile.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "포트폴리오 파일을 찾을 수 없습니다.");
        }
        boolean linked = linkedAssets(userId, profile.getId()).stream()
                .anyMatch(asset -> asset.getId().equals(fileId));
        if (!linked) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "포트폴리오 파일을 찾을 수 없습니다.");
        }
        fileService.deleteOwnedLinked(userId, fileId, FILE_KIND, REF_TYPE, profile.getId());
    }

    /**
     * AutoPrep 프로필 단계가 포트폴리오를 별도 의미로 참고할 수 있게 만든 제한된 텍스트 컨텍스트.
     * 자소서 attachment 목록에는 합치지 않으므로 SELF_INTRO 첨삭으로 오분류되지 않는다.
     */
    public String evidenceText(Long userId) {
        UserProfile profile = profileMapper.findByUserId(userId);
        if (profile == null || profile.getId() == null) {
            return null;
        }
        StringBuilder evidence = new StringBuilder();
        List<FileAsset> assets = linkedAssets(userId, profile.getId());
        for (int i = 0; i < Math.min(assets.size(), MAX_EVIDENCE_FILES); i++) {
            FileAsset asset = assets.get(i);
            if (!evidence.isEmpty()) {
                evidence.append("\n\n");
            }
            evidence.append("[포트폴리오 파일] ").append(asset.getOriginalName());
            try {
                FileService.Download download = fileService.download(userId, asset.getId());
                Extraction extraction = documentTextExtractor.extract(
                        download.bytes(), asset.getContentType(), asset.getOriginalName());
                if (extraction.isSuccess() && extraction.text() != null && !extraction.text().isBlank()) {
                    evidence.append("\n").append(extraction.text().trim());
                } else {
                    evidence.append("\n(텍스트를 추출할 수 없는 파일 — 파일명과 메타데이터만 참고)");
                }
            } catch (RuntimeException ex) {
                log.warn("포트폴리오 AI 참고 텍스트 로드 실패 fileId={}: {}", asset.getId(), ex.getMessage());
                evidence.append("\n(파일 내용을 불러오지 못함)");
            }
            if (evidence.length() >= MAX_EVIDENCE_CHARS) {
                return evidence.substring(0, MAX_EVIDENCE_CHARS);
            }
        }
        return evidence.isEmpty() ? null : evidence.toString();
    }

    private UserProfile ensureProfile(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        profileMapper.insertEmptyIfAbsent(userId);
        // 사용자 프로필 행을 잠가 upload/link의 개수 확인과 연결을 직렬화한다.
        UserProfile profile = profileMapper.findByUserIdForUpdate(userId);
        if (profile == null || profile.getId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "프로필을 준비하지 못했습니다.");
        }
        return profile;
    }

    private List<FileAsset> linkedAssets(Long userId, Long profileId) {
        return fileService.findLinkedFiles(REF_TYPE, profileId).stream()
                .filter(asset -> userId.equals(asset.getOwnerUserId()))
                .filter(asset -> FILE_KIND.equals(asset.getKind()))
                .toList();
    }
}
