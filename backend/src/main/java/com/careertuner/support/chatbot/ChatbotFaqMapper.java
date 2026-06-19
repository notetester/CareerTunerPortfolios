package com.careertuner.support.chatbot;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.support.domain.Faq;

@Mapper
public interface ChatbotFaqMapper {

    /** 발행된 FAQ 중 임베딩이 없는 것 조회 */
    List<Faq> findPublishedWithoutEmbedding();

    /** 발행된 FAQ 전체 조회 (임베딩 포함) */
    List<Faq> findPublishedAll();

    /** 임베딩이 있는 발행 FAQ 조회 (검색용) */
    List<Faq> findPublishedWithEmbedding();

    /** 임베딩 업데이트 */
    void updateEmbedding(@Param("id") Long id, @Param("embedding") String embedding);
}
