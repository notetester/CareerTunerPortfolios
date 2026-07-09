package com.careertuner.user.service;

import com.careertuner.user.dto.AccountInfoResponse;
import com.careertuner.user.dto.UserResumeDetailRequest;
import com.careertuner.user.dto.UserResumeDetailResponse;

/** 계정 확충(로그인 아이디·전화번호·연결 계정) + 이력서 상세 스펙. */
public interface UserAccountService {

    AccountInfoResponse accountInfo(Long userId);

    /** 로그인 아이디 최초 설정(설정 후 변경 불가). */
    AccountInfoResponse setLoginId(Long userId, String loginId);

    /** 전화번호 설정/변경(전역 UNIQUE, 인증은 선택적·스텁). */
    AccountInfoResponse setPhone(Long userId, String phone);

    /** 실제 이메일 등록/변경 인증 메일 발송. 링크 인증 완료 전까지는 기존 이메일을 유지한다. */
    void requestEmailRegistration(Long userId, String email);

    AccountInfoResponse unlinkSocial(Long userId, String provider);

    UserResumeDetailResponse getResumeDetail(Long userId);

    UserResumeDetailResponse saveResumeDetail(Long userId, UserResumeDetailRequest request);
}
