package com.careertuner.ad.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.ad.domain.AdCampaign;

@Mapper
public interface AdCampaignMapper {

    List<AdCampaign> findVisible(@Param("surface") String surface, @Param("limit") int limit);

    List<AdCampaign> findAdmin(@Param("surface") String surface,
                               @Param("active") Boolean active,
                               @Param("keyword") String keyword,
                               @Param("limit") int limit);

    AdCampaign findById(@Param("id") Long id);

    void insert(AdCampaign campaign);

    void update(AdCampaign campaign);

    void insertEvent(@Param("campaignId") Long campaignId,
                     @Param("userId") Long userId,
                     @Param("surface") String surface,
                     @Param("eventType") String eventType);
}
