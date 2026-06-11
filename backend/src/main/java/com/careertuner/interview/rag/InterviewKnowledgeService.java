package com.careertuner.interview.rag;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.common.security.AuthUser;
import com.careertuner.interview.domain.InterviewKnowledge;

/**
 * 면접 RAG 지식베이스 서비스.
 * 원본은 MySQL, 벡터는 Qdrant. retrieve 는 best-effort(비활성/장애 시 빈 컨텍스트)로 동작해
 * Qdrant 가 없어도 면접 평가 흐름을 끊지 않는다.
 */
@Service
public class InterviewKnowledgeService {

    private static final Set<String> KINDS = Set.of("RUBRIC", "QUESTION_BANK", "COMPANY", "GENERAL");

    private final InterviewKnowledgeMapper mapper;
    private final EmbeddingClient embeddingClient;
    private final QdrantClient qdrantClient;
    private final InterviewRagProperties properties;

    public InterviewKnowledgeService(InterviewKnowledgeMapper mapper, EmbeddingClient embeddingClient,
                                     QdrantClient qdrantClient, InterviewRagProperties properties) {
        this.mapper = mapper;
        this.embeddingClient = embeddingClient;
        this.qdrantClient = qdrantClient;
        this.properties = properties;
    }

    @Transactional
    public InterviewKnowledge addDocument(AuthUser authUser, String kind, String title,
                                          String content, String source) {
        requireAdmin(authUser);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지식 내용을 입력해 주세요.");
        }
        InterviewKnowledge doc = InterviewKnowledge.builder()
                .kind(normalizeKind(kind))
                .title(title)
                .content(content)
                .source(source)
                .indexed(false)
                .build();
        mapper.insert(doc);

        // 색인은 best-effort. Qdrant 미가동 시 indexed=false 로 남고 나중에 reindex 가능.
        if (properties.isEnabled()) {
            try {
                indexOne(doc);
                mapper.markIndexed(doc.getId());
                doc.setIndexed(true);
            } catch (RuntimeException ex) {
                // 본문 저장은 유지, 색인만 실패.
                doc.setIndexed(false);
            }
        }
        return doc;
    }

    @Transactional(readOnly = true)
    public List<InterviewKnowledge> list(AuthUser authUser, int limit) {
        requireAdmin(authUser);
        return mapper.findAll(limit <= 0 ? 100 : Math.min(limit, 500));
    }

    /** 전체 문서를 Qdrant 에 재색인한다. */
    @Transactional
    public int reindexAll(AuthUser authUser) {
        requireAdmin(authUser);
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "RAG 가 비활성화되어 있습니다(careertuner.interview.rag.enabled).");
        }
        qdrantClient.ensureCollection();
        List<InterviewKnowledge> docs = mapper.findAll(500);
        int count = 0;
        for (InterviewKnowledge doc : docs) {
            indexOne(doc);
            mapper.markIndexed(doc.getId());
            count++;
        }
        return count;
    }

    /**
     * 질의와 유사한 지식 스니펫을 프롬프트 주입용 문자열로 반환한다.
     * 비활성/장애/결과없음 → 빈 문자열(호출부는 근거 없이 진행).
     */
    public String retrieveContext(String query) {
        if (!properties.isEnabled() || query == null || query.isBlank()) {
            return "";
        }
        try {
            List<Float> vector = embeddingClient.embedAsList(query);
            List<QdrantClient.SearchHit> hits = qdrantClient.search(vector, properties.getTopK());
            if (hits.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (QdrantClient.SearchHit hit : hits) {
                if (hit.text() == null || hit.text().isBlank()) {
                    continue;
                }
                sb.append(i++).append(". ");
                if (hit.title() != null && !hit.title().isBlank()) {
                    sb.append("[").append(hit.title()).append("] ");
                }
                sb.append(hit.text().strip()).append("\n");
            }
            return sb.toString();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    // ───── 내부 ─────

    private void indexOne(InterviewKnowledge doc) {
        qdrantClient.ensureCollection();
        List<Float> vector = embeddingClient.embedAsList(textForEmbedding(doc));
        Map<String, Object> payload = Map.of(
                "content", doc.getContent(),
                "kind", doc.getKind() == null ? "" : doc.getKind(),
                "title", doc.getTitle() == null ? "" : doc.getTitle());
        qdrantClient.upsert(doc.getId(), vector, payload);
    }

    private String textForEmbedding(InterviewKnowledge doc) {
        String title = doc.getTitle() == null ? "" : doc.getTitle();
        return (title + "\n" + doc.getContent()).strip();
    }

    private String normalizeKind(String kind) {
        String upper = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
        if (!KINDS.contains(upper)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 지식 종류입니다.");
        }
        return upper;
    }

    private void requireAdmin(AuthUser authUser) {
        if (authUser == null || !"ADMIN".equals(authUser.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "관리자 권한이 필요합니다.");
        }
    }
}
