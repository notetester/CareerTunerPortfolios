package com.careertuner.admin.legal.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.admin.legal.dto.AdminLegalVersionDetail;
import com.careertuner.admin.legal.dto.AdminLegalVersionResponse;
import com.careertuner.admin.legal.dto.CreateLegalDraftRequest;
import com.careertuner.admin.legal.dto.PublishLegalRequest;
import com.careertuner.admin.legal.dto.PublishLegalResponse;
import com.careertuner.admin.legal.dto.SaveLegalDraftRequest;
import com.careertuner.admin.legal.mapper.AdminLegalMapper;
import com.careertuner.admin.legal.mapper.AdminVersionRow;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.legal.domain.LegalClause;
import com.careertuner.legal.domain.LegalDocType;
import com.careertuner.legal.domain.LegalDocumentVersion;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLegalServiceImpl implements AdminLegalService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 불리한 변경 시 권장 리드타임(일). 미만이면 경고. */
    private static final int ADVERSE_LEAD_DAYS = 30;
    /** 일반 변경 권장 리드타임(일). */
    private static final int NORMAL_LEAD_DAYS = 7;

    /** KST 단일 시계(+09:00, 한국은 DST 없음). DB 세션 +09:00 고정과 동일 기준. */
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final AdminLegalMapper adminLegalMapper;

    @Override
    public List<AdminLegalVersionResponse> getVersions(AuthUser authUser, String docType) {
        requireAdmin(authUser);
        LegalDocType type = LegalDocType.from(docType);
        List<AdminVersionRow> rows = adminLegalMapper.findVersionsByDocType(type.dbValue());

        Long liveId = computeLiveId(rows);
        LocalDateTime now = LocalDateTime.now(KST);

        List<AdminLegalVersionResponse> result = new ArrayList<>(rows.size());
        for (AdminVersionRow r : rows) {
            String badge = computeBadge(r, liveId, now);
            result.add(new AdminLegalVersionResponse(
                    r.getId(), r.getDocType(), r.getVersionLabel(), r.getStatus(), badge,
                    r.getSummary(), r.isAdverse(), r.getEffectiveDate(), r.getPublishedAt(),
                    r.getCreatedAt(), r.getUpdatedAt(), r.getClauseCount()));
        }
        return result;
    }

    @Override
    public AdminLegalVersionDetail getVersionDetail(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        LegalDocumentVersion version = requireVersion(id);
        return toDetail(version, badgeForVersion(version));
    }

    @Override
    @Transactional
    public AdminLegalVersionDetail createDraft(AuthUser authUser, String docType, CreateLegalDraftRequest request) {
        requireAdmin(authUser);
        LegalDocType type = LegalDocType.from(docType);

        // doc_type별 DRAFT 1건 제약.
        if (adminLegalMapper.countDrafts(type.dbValue()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "이미 작성 중인 초안이 있습니다. 기존 초안을 게시하거나 삭제한 뒤 새 초안을 만들 수 있습니다.");
        }

        String label = request != null && request.versionLabel() != null && !request.versionLabel().isBlank()
                ? request.versionLabel().trim()
                : "초안";

        LegalDocumentVersion draft = LegalDocumentVersion.builder()
                .docType(type.dbValue())
                .versionLabel(label)
                .status(STATUS_DRAFT)
                .adverse(false)
                .adminId(authUser.id())
                .build();
        adminLegalMapper.insertVersion(draft);
        Long newId = draft.getId();

        // 현행 시행본 조항 복제 옵션.
        boolean clone = request != null && Boolean.TRUE.equals(request.cloneFromCurrent());
        if (clone) {
            LegalDocumentVersion live = adminLegalMapper.findLiveVersion(type.dbValue());
            if (live != null) {
                List<LegalClause> source = adminLegalMapper.findClausesByVersionId(live.getId());
                List<LegalClause> cloned = new ArrayList<>(source.size());
                int seq = 1;
                for (LegalClause c : source) {
                    cloned.add(LegalClause.builder()
                            .versionId(newId)
                            .seq(seq++)
                            .title(c.getTitle())
                            .body(c.getBody())
                            .build());
                }
                if (!cloned.isEmpty()) {
                    adminLegalMapper.insertClauses(newId, cloned);
                }
            }
        }

        LegalDocumentVersion saved = requireVersion(newId);
        return toDetail(saved, STATUS_DRAFT.equals(saved.getStatus()) ? "draft" : badgeForVersion(saved));
    }

    @Override
    @Transactional
    public AdminLegalVersionDetail saveDraft(AuthUser authUser, Long id, SaveLegalDraftRequest request) {
        requireAdmin(authUser);
        LegalDocumentVersion version = requireVersion(id);
        if (!STATUS_DRAFT.equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "게시된 버전은 수정할 수 없습니다. 초안만 저장 가능합니다.");
        }

        String label = request.versionLabel() != null && !request.versionLabel().isBlank()
                ? request.versionLabel().trim()
                : version.getVersionLabel();
        String summary = request.summary() != null ? request.summary() : version.getSummary();
        boolean adverse = request.isAdverse() != null ? request.isAdverse() : version.isAdverse();
        LocalDateTime effectiveDate = request.effectiveDate() != null
                ? request.effectiveDate().toLocalDate().atStartOfDay() // 00:00 KST 자정 정규화
                : version.getEffectiveDate();

        adminLegalMapper.updateDraftMeta(id, label, summary, adverse, effectiveDate);

        // clauses 가 제공되면 통째 교체.
        if (request.clauses() != null) {
            adminLegalMapper.deleteClausesByVersionId(id);
            List<LegalClause> toInsert = new ArrayList<>();
            int seq = 1;
            for (SaveLegalDraftRequest.ClauseInput in : request.clauses()) {
                if (in == null) {
                    continue;
                }
                int useSeq = in.seq() != null ? in.seq() : seq;
                toInsert.add(LegalClause.builder()
                        .versionId(id)
                        .seq(useSeq)
                        .title(in.title() != null ? in.title() : "")
                        .body(in.body() != null ? in.body() : "")
                        .build());
                seq++;
            }
            if (!toInsert.isEmpty()) {
                adminLegalMapper.insertClauses(id, toInsert);
            }
        }

        return toDetail(requireVersion(id), "draft");
    }

    @Override
    @Transactional
    public PublishLegalResponse publish(AuthUser authUser, Long id, PublishLegalRequest request) {
        requireAdmin(authUser);
        LegalDocumentVersion version = requireVersion(id);

        // DRAFT 일 때만 게시 가능.
        if (!STATUS_DRAFT.equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "이미 게시된 버전입니다. 초안만 게시할 수 있습니다.");
        }

        // 조항 0개 게시 차단.
        if (adminLegalMapper.countClauses(id) < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조항이 없는 버전은 게시할 수 없습니다.");
        }

        // 시행일 정규화(00:00 KST 자정) + 백데이트 가드.
        // 약관은 존재 전(게시일 이전 날짜)부터 효력을 가질 수 없다 — 당일(즉시)만 허용.
        LocalDate today = LocalDate.now(KST);
        LocalDateTime effectiveDate;
        if (request != null && request.effectiveDate() != null) {
            LocalDate effDate = request.effectiveDate().toLocalDate();
            if (effDate.isBefore(today)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "시행일은 게시일(오늘) 이전일 수 없습니다.");
            }
            effectiveDate = effDate.atStartOfDay();
        } else {
            effectiveDate = today.atStartOfDay(); // 즉시 = 오늘 00:00 KST
        }

        // is_adverse 리드타임 경고(차단 아님). 날짜 기준 일수.
        String warning = computeLeadTimeWarning(version.isAdverse(), today, effectiveDate.toLocalDate());

        adminLegalMapper.publishVersion(id, effectiveDate);

        LegalDocumentVersion published = requireVersion(id);
        return new PublishLegalResponse(toDetail(published, badgeForVersion(published)), warning);
    }

    @Override
    @Transactional
    public void deleteVersion(AuthUser authUser, Long id) {
        requireAdmin(authUser);
        LegalDocumentVersion version = requireVersion(id);
        if (!STATUS_DRAFT.equals(version.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "게시된 버전은 삭제할 수 없습니다(법적/감사 보존). 초안만 삭제 가능합니다.");
        }
        adminLegalMapper.deleteVersion(id); // 조항은 FK CASCADE.
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }

    private LegalDocumentVersion requireVersion(Long id) {
        LegalDocumentVersion version = adminLegalMapper.findVersionById(id);
        if (version == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "버전을 찾을 수 없습니다.");
        }
        return version;
    }

    /**
     * 목록 행들에서 live 버전 id 를 계산한다.
     * live = PUBLISHED & effective_date<=NOW() 중 effective_date 최댓값 1건,
     * 동률이면 effective_date DESC, published_at DESC, id DESC.
     */
    private Long computeLiveId(List<AdminVersionRow> rows) {
        LocalDateTime now = LocalDateTime.now(KST);
        AdminVersionRow best = null;
        for (AdminVersionRow r : rows) {
            if (!STATUS_PUBLISHED.equals(r.getStatus())) {
                continue;
            }
            if (r.getEffectiveDate() == null || r.getEffectiveDate().isAfter(now)) {
                continue;
            }
            if (best == null || isMoreLive(r, best)) {
                best = r;
            }
        }
        return best != null ? best.getId() : null;
    }

    /** tiebreaker: effective_date DESC, published_at DESC, id DESC. */
    private boolean isMoreLive(AdminVersionRow candidate, AdminVersionRow current) {
        int cmp = candidate.getEffectiveDate().compareTo(current.getEffectiveDate());
        if (cmp != 0) {
            return cmp > 0;
        }
        cmp = compareNullsFirst(candidate.getPublishedAt(), current.getPublishedAt());
        if (cmp != 0) {
            return cmp > 0;
        }
        return candidate.getId() > current.getId();
    }

    private int compareNullsFirst(LocalDateTime a, LocalDateTime b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    /**
     * 배지 계산.
     *   draft = DRAFT
     *   next  = PUBLISHED & effective_date>NOW()
     *   live  = liveId 와 일치
     *   old   = PUBLISHED & effective_date<=NOW() 인데 live 아님
     */
    private String computeBadge(AdminVersionRow r, Long liveId, LocalDateTime now) {
        if (STATUS_DRAFT.equals(r.getStatus())) {
            return "draft";
        }
        if (r.getEffectiveDate() == null || r.getEffectiveDate().isAfter(now)) {
            return "next";
        }
        if (liveId != null && liveId.equals(r.getId())) {
            return "live";
        }
        return "old";
    }

    /** 단일 버전 배지 계산 — 같은 doc_type 의 목록을 다시 조회해 live 를 판정한다. */
    private String badgeForVersion(LegalDocumentVersion version) {
        if (STATUS_DRAFT.equals(version.getStatus())) {
            return "draft";
        }
        LocalDateTime now = LocalDateTime.now(KST);
        if (version.getEffectiveDate() == null || version.getEffectiveDate().isAfter(now)) {
            return "next";
        }
        List<AdminVersionRow> rows = adminLegalMapper.findVersionsByDocType(version.getDocType());
        Long liveId = computeLiveId(rows);
        return liveId != null && liveId.equals(version.getId()) ? "live" : "old";
    }

    private String computeLeadTimeWarning(boolean adverse, LocalDate today, LocalDate effectiveDate) {
        int requiredDays = adverse ? ADVERSE_LEAD_DAYS : NORMAL_LEAD_DAYS;
        long leadDays = ChronoUnit.DAYS.between(today, effectiveDate);
        if (leadDays < requiredDays) {
            return String.format(
                    "%s 변경의 권장 공지 기간(%d일)보다 시행일까지 남은 기간이 짧습니다(약 %d일). 공지 기간을 확인하세요.",
                    adverse ? "회원에게 불리한" : "일반", requiredDays, Math.max(leadDays, 0));
        }
        return null;
    }

    private AdminLegalVersionDetail toDetail(LegalDocumentVersion version, String badge) {
        List<LegalClause> clauses = adminLegalMapper.findClausesByVersionId(version.getId());
        List<AdminLegalVersionDetail.ClauseDto> clauseDtos = clauses.stream()
                .map(c -> new AdminLegalVersionDetail.ClauseDto(c.getId(), c.getSeq(), c.getTitle(), c.getBody()))
                .toList();
        return new AdminLegalVersionDetail(
                version.getId(), version.getDocType(), version.getVersionLabel(), version.getStatus(),
                badge, version.getSummary(), version.isAdverse(), version.getEffectiveDate(),
                version.getPublishedAt(), version.getCreatedAt(), version.getUpdatedAt(), clauseDtos);
    }
}
