package com.careertuner.enterprise.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.enterprise.domain.EnterpriseAccountApplication;
import com.careertuner.enterprise.domain.EnterpriseJobPolicy;
import com.careertuner.enterprise.domain.EnterpriseJobPosting;

@Mapper
public interface EnterpriseMapper {

    EnterpriseAccountApplication findLatestApplicationByUserId(@Param("userId") Long userId);

    EnterpriseAccountApplication findApplicationById(@Param("id") Long id);

    List<EnterpriseAccountApplication> findApplications(@Param("status") String status,
                                                        @Param("keyword") String keyword,
                                                        @Param("limit") int limit);

    void insertApplication(EnterpriseAccountApplication application);

    void reviewApplication(@Param("id") Long id,
                           @Param("status") String status,
                           @Param("reviewMemo") String reviewMemo,
                           @Param("reviewedBy") Long reviewedBy);

    EnterpriseJobPolicy findPolicyByUserId(@Param("userId") Long userId);

    void upsertPolicy(@Param("userId") Long userId,
                      @Param("trusted") boolean trusted,
                      @Param("createRequiresReview") boolean createRequiresReview,
                      @Param("editRequiresReview") boolean editRequiresReview,
                      @Param("maxActivePosts") int maxActivePosts,
                      @Param("updatedBy") Long updatedBy,
                      @Param("updateReason") String updateReason);

    int countActiveJobsByUserId(@Param("userId") Long userId);

    void insertJob(EnterpriseJobPosting job);

    void updateJob(EnterpriseJobPosting job);

    void updateJobPendingRevision(@Param("id") Long id,
                                  @Param("pendingRevisionJson") String pendingRevisionJson,
                                  @Param("reviewStatus") String reviewStatus,
                                  @Param("reviewMemo") String reviewMemo);

    void updateCommunityPostId(@Param("id") Long id, @Param("communityPostId") Long communityPostId);

    EnterpriseJobPosting findJobById(@Param("id") Long id);

    List<EnterpriseJobPosting> findJobsByOwner(@Param("userId") Long userId);

    List<EnterpriseJobPosting> findPublicJobs(@Param("keyword") String keyword, @Param("limit") int limit);

    List<EnterpriseJobPosting> findAdminJobs(@Param("status") String status,
                                             @Param("keyword") String keyword,
                                             @Param("limit") int limit);
}
