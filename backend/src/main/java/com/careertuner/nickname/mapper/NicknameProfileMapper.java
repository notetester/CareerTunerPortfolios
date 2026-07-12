package com.careertuner.nickname.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.nickname.domain.AccountNameRow;
import com.careertuner.nickname.domain.ConversationMemberProfile;
import com.careertuner.nickname.domain.NicknameProfile;

@Mapper
public interface NicknameProfileMapper {

    // ── 닉네임 프로필 ──

    /** id 는 useGeneratedKeys 로 채워진다. */
    void insert(NicknameProfile profile);

    NicknameProfile findById(Long id);

    List<NicknameProfile> findByUserId(Long userId);

    /** 계정의 기본(is_default=1) 프로필. 없으면 null. */
    NicknameProfile findDefaultByUserId(Long userId);

    /** 표시명 벌크 해석용 — ACTIVE 프로필을 id 집합으로 한 번에 조회(N+1 방지). */
    List<NicknameProfile> findActiveByIds(@Param("ids") Collection<Long> ids);

    /** 표시명 벌크 해석용 — 계정별 기본(우선) 프로필을 한 번에 조회. 계정당 1행. */
    List<NicknameProfile> findDefaultsByUserIds(@Param("userIds") Collection<Long> userIds);

    /** 표시명 벌크 해석용 — 계정명(users.name) 폴백을 한 번에 조회. */
    List<AccountNameRow> findAccountNames(@Param("userIds") Collection<Long> userIds);

    int countByUserId(Long userId);

    /** 닉네임 전역 중복 검사(자기 자신 제외). */
    int countByNicknameExcluding(@Param("nickname") String nickname, @Param("excludeId") Long excludeId);

    void update(NicknameProfile profile);

    /** 소유권 확인용 소프트 삭제(status=HIDDEN). 실제 행은 유지(작성 이력 참조). */
    int hide(@Param("id") Long id, @Param("userId") Long userId);

    /** 계정의 모든 프로필 is_default=0 으로 초기화(기본 재지정 전 단계). */
    void clearDefault(Long userId);

    /** 지정 프로필을 기본으로(소유자 검증 포함). */
    int markDefault(@Param("id") Long id, @Param("userId") Long userId);

    // ── 채팅방 전용 프로필 매핑 ──

    ConversationMemberProfile findConversationProfile(@Param("conversationId") Long conversationId,
                                                      @Param("userId") Long userId);

    void upsertConversationProfile(ConversationMemberProfile profile);

    /** 대화방의 활성 멤버인지 확인(collaboration 도메인 읽기 전용 조회). */
    int isActiveMember(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    /** 계정 표시명 폴백용(users.name). */
    String findAccountName(Long userId);

    /** 공개 표시명 해석 시 탈퇴 계정의 식별자 링크를 제거하기 위한 현재 상태. */
    String findAccountStatus(Long userId);
}
