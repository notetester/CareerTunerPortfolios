package com.careertuner.file.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.file.domain.FileAsset;

@Mapper
public interface FileAssetMapper {

    void insert(FileAsset asset);

    FileAsset findById(@Param("id") Long id);

    List<FileAsset> findByRef(@Param("refType") String refType, @Param("refId") Long refId);

    void updateRef(@Param("id") Long id,
                   @Param("refType") String refType,
                   @Param("refId") Long refId);

    int updateRefIfOwnedAndUnlinked(@Param("id") Long id,
                                    @Param("ownerUserId") Long ownerUserId,
                                    @Param("expectedKind") String expectedKind,
                                    @Param("refType") String refType,
                                    @Param("refId") Long refId);

    /** 업로드 시 선언한 용도까지 재검증하며 대기 파일을 도메인 row에 귀속한다. */
    int claimOwnedPendingFile(@Param("id") Long id,
                              @Param("ownerUserId") Long ownerUserId,
                              @Param("expectedKind") String expectedKind,
                              @Param("expectedRefType") String expectedRefType,
                              @Param("refId") Long refId);

    int deleteByIdAndOwnerIfPending(@Param("id") Long id,
                                    @Param("ownerUserId") Long ownerUserId);

    /** 메신저 전송 대기 첨부를 메시지에 원자적으로 귀속한다. */
    int claimPendingCollaborationAttachment(@Param("id") Long id,
                                            @Param("ownerUserId") Long ownerUserId,
                                            @Param("messageId") Long messageId);

    List<FileAsset> findStalePendingCollaborationAttachments(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    int deleteStalePendingCollaborationAttachment(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("cutoff") LocalDateTime cutoff);

    List<FileAsset> findStalePendingInterviewMedia(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    int deleteStalePendingInterviewMedia(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("cutoff") LocalDateTime cutoff);

    int deleteByIdAndOwnerAndRef(@Param("id") Long id,
                                 @Param("ownerUserId") Long ownerUserId,
                                 @Param("expectedKind") String expectedKind,
                                 @Param("refType") String refType,
                                 @Param("refId") Long refId);
}
