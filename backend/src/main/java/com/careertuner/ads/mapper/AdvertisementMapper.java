package com.careertuner.ads.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.ads.domain.Advertisement;

@Mapper
public interface AdvertisementMapper {

    // ── 공개 노출 ──

    /**
     * 활성·기간 내·플랫폼 매치 광고 후보를 priority DESC, id DESC 로 반환한다.
     * 가중 랜덤 선택은 서비스에서 처리한다(weight 반영).
     */
    List<Advertisement> findServable(@Param("placement") String placement,
                                     @Param("platform") String platform,
                                     @Param("now") LocalDateTime now);

    Advertisement findById(@Param("id") Long id);

    void increaseImpression(@Param("id") Long id);

    void increaseClick(@Param("id") Long id);

    /** 광고 노출 게이트용 — users.plan 직접 조회(FREE 아니면 광고 제외). */
    String findUserPlan(@Param("userId") Long userId);

    // ── 관리자 ──

    void insert(Advertisement ad);

    void update(Advertisement ad);

    void updateActive(@Param("id") Long id, @Param("active") boolean active);

    void delete(@Param("id") Long id);

    /** 관리자 목록 — placement/platform/activeOnly 선택 필터. 최신 등록순. */
    List<Advertisement> findForAdmin(@Param("placement") String placement,
                                     @Param("platform") String platform,
                                     @Param("activeOnly") boolean activeOnly);
}
