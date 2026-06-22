package com.careertuner.admin.legal.service;

import java.util.List;

import com.careertuner.admin.legal.dto.AdminLegalVersionDetail;
import com.careertuner.admin.legal.dto.AdminLegalVersionResponse;
import com.careertuner.admin.legal.dto.CreateLegalDraftRequest;
import com.careertuner.admin.legal.dto.PublishLegalRequest;
import com.careertuner.admin.legal.dto.PublishLegalResponse;
import com.careertuner.admin.legal.dto.SaveLegalDraftRequest;
import com.careertuner.common.security.AuthUser;

public interface AdminLegalService {

    /** docType 의 버전 목록 (live/next/old/draft 배지 계산 포함). */
    List<AdminLegalVersionResponse> getVersions(AuthUser authUser, String docType);

    /** 버전 상세 + 조항. */
    AdminLegalVersionDetail getVersionDetail(AuthUser authUser, Long id);

    /** 새 초안 생성 (옵션: 현행 조항 복제). doc_type별 DRAFT 1건 제약. */
    AdminLegalVersionDetail createDraft(AuthUser authUser, String docType, CreateLegalDraftRequest request);

    /** 초안 저장 (DRAFT 만). clauses 제공 시 통째 교체. */
    AdminLegalVersionDetail saveDraft(AuthUser authUser, Long id, SaveLegalDraftRequest request);

    /** 게시 (DRAFT 만, 조항≥1). 리드타임 부족 시 경고만(차단 아님). */
    PublishLegalResponse publish(AuthUser authUser, Long id, PublishLegalRequest request);

    /** 삭제 (DRAFT 만). */
    void deleteVersion(AuthUser authUser, Long id);
}
