package com.careertuner.community.moderation.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.domain.PostStatus;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.client.ModerationLlmGateway;
import com.careertuner.community.moderation.domain.AiTaskType;
import com.careertuner.community.moderation.dto.ModerationImage;
import com.careertuner.community.moderation.dto.ModerationResult;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;
import com.careertuner.notification.domain.Notification;
import com.careertuner.notification.service.NotificationService;

import tools.jackson.databind.ObjectMapper;

/**
 * 커뮤니티 글 <b>본문 첨부 이미지</b> AI 검열 서비스.
 *
 * <p>텍스트 검열({@link PostModerationService})이 글 전체를 PUBLISHED→HIDDEN 으로 숨기는 것과 달리,
 * 이미지 검열은 vision 판정이 불완전하다는 점을 고려해 <b>글을 숨기지 않고</b> 문제 이미지 URL 만
 * {@code post_ai_result}(IMAGE_MODERATION)에 남긴다. 프런트는 그 목록의 이미지만 블러 + 클릭하여 보기로
 * 처리한다(soft action). 판정은 로컬 gemma4 vision 우선, 실패 시 Claude/OpenAI vision 으로 폴백하며
 * 모두 실패하면 fail-open(블러 없음).
 *
 * <p>{@code @Transactional 금지} — vision 호출이 길어 커넥션 풀 점유 위험(텍스트 검열과 동일 원칙).
 */
@Service
public class PostImageModerationService {

    private static final Logger log = LoggerFactory.getLogger(PostImageModerationService.class);

    /** 글 1건당 판정 이미지 상한 — 비용·시간 폭주 방지. 초과분은 검열하지 않는다(로그로 남김). */
    private static final int MAX_IMAGES = 6;
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    /**
     * abuse(음란·불법·폭력) 이미지를 글 숨김(하드 액션)으로 올릴 최소 확신도.
     * vision 오판을 감안해 블러 임계(설정 hideThreshold)보다 높게 잡는다 —
     * 고신뢰 유해물만 숨기고, 애매하면 블러(소프트)로 남긴다.
     */
    private static final double ABUSE_HIDE_MIN_CONFIDENCE = 0.85;

    /** 본문 HTML 의 <img src="..."> 추출(정화 후 HTML 은 항상 큰따옴표 속성). */
    private static final Pattern IMG_SRC =
            Pattern.compile("<img\\b[^>]*?\\bsrc\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    /** 이미지 판정 스키마 — 텍스트(normal/abuse/spam/ad)에 개인정보 노출(pii) 카테고리를 더한다. */
    private static final Map<String, Object> MODERATION_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "toxic", Map.of("type", "boolean"),
                    "category", Map.of("type", "string",
                            "enum", List.of("normal", "abuse", "spam", "ad", "pii", "gross")),
                    "confidence", Map.of("type", "number")
            ),
            "required", List.of("toxic", "category", "confidence")
    );

    private static final String IMAGE_SYSTEM_PROMPT = """
            너는 커뮤니티 게시글 첨부 이미지를 심사한다. 이미지 한 장을 보고 아래 카테고리 중 하나로 분류해 JSON 으로만 답하라.
            판정 원칙: 위반 소지가 조금이라도 뚜렷하면 normal 로 두지 말고 해당 카테고리로 잡는다(과소 차단 금지).
            - abuse : 폭력·유혈(피·상처·멍이 보임), 선정성/노출, 잔혹, 불법(마약·무기 등), 차별·혐오 표현. 스포츠·격투 장면이라도 얼굴이나 몸에 피가 보이면 abuse.
            - gross : 지네·바퀴벌레·거미·구더기 등 다리 많거나 징그러운 벌레, 배설물(똥·오줌)·구토 등 강한 혐오·불쾌감을 주는 이미지(불법·음란은 아님).
            - pii   : 사람 이름과 함께 전화번호·이메일·생년월일·주소·주민번호·계좌 중 하나라도 보이면 pii. 이력서·명함·신분증·서류·화면 캡처여도 개인정보가 보이면 무조건 pii.
            - ad    : 할인·세일·가격·쿠폰·이벤트 문구, 브랜드/서비스/강의 홍보, 구매·가입·신청 유도가 담긴 이미지(포스터·배너·전단지 포함).
            - spam  : 본문과 무관한 도배/낚시성 이미지.
            - normal: 위 어디에도 뚜렷이 해당하지 않는 평범한 이미지(일반 인물·풍경·음식·제품 사진, 개인정보 없는 화면 캡처 등).
            - toxic: category 가 normal 이면 false, 그 외(abuse/gross/pii/ad/spam)면 true
            - confidence: 0.0~1.0 사이의 확신도
            텍스트 설명 없이 JSON 객체 하나만 출력한다.
            """;

    private static final String IMAGE_USER_TEXT =
            "위 이미지를 판정하라. 이름과 함께 전화번호·이메일·생년월일 등 개인정보가 보이면 반드시 pii, "
            + "얼굴·몸에 피가 보이면 abuse, 징그러운 벌레·배설물이면 gross 로 잡아라.";

    private final ModerationLlmGateway gateway;
    private final PostAiResultMapper aiResultMapper;
    private final CommunityPostMapper postMapper;
    private final ModerationSettingService settingService;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final UserSanctionService userSanctionService;
    private final HttpClient httpClient;

    public PostImageModerationService(ModerationLlmGateway gateway,
                                      PostAiResultMapper aiResultMapper,
                                      CommunityPostMapper postMapper,
                                      ModerationSettingService settingService,
                                      ObjectMapper objectMapper,
                                      NotificationService notificationService,
                                      UserSanctionService userSanctionService) {
        this.gateway = gateway;
        this.aiResultMapper = aiResultMapper;
        this.postMapper = postMapper;
        this.settingService = settingService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.userSanctionService = userSanctionService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /** 본문 이미지 검열 파이프라인 — 이벤트 리스너가 비동기로 호출한다(@Transactional 금지). */
    public void moderate(Long postId) {
        try {
            aiResultMapper.upsertPending(postId, AiTaskType.IMAGE_MODERATION);

            CommunityPost post = postMapper.findById(postId);
            if (post == null || PostStatus.DELETED.name().equals(post.getStatus())) {
                log.info("이미지 검열 스킵: postId={} (삭제됨 또는 없음)", postId);
                return;
            }

            List<String> urls = extractImageUrls(post.getContent());
            double blurThreshold = settingService.getHideThreshold();

            List<Map<String, Object>> perImage = new ArrayList<>();
            List<String> flagged = new ArrayList<>();   // 블러(소프트) 대상 URL
            boolean hide = false;                        // abuse(고신뢰) → 글 숨김(하드)
            String model = "none";
            int processed = 0;

            for (String url : urls) {
                if (processed >= MAX_IMAGES) {
                    log.warn("이미지 검열 상한({}) 초과 postId={} — 나머지 이미지 스킵", MAX_IMAGES, postId);
                    break;
                }
                // Cloudinary(http/https) 이미지를 다운로드해 판정한다. 다운로드 실패는 fail-open 으로 건너뛴다.
                ModerationImage image = fetch(url);
                if (image == null) {
                    continue; // 다운로드/해상 실패 → fail-open(블러 안 함)
                }
                processed++;

                ModerationLlmGateway.LlmReply reply =
                        gateway.chatVision(IMAGE_SYSTEM_PROMPT, IMAGE_USER_TEXT, List.of(image), MODERATION_SCHEMA);
                if (reply.mock()) {
                    continue; // 모든 vision provider 실패 → fail-open
                }
                ModerationResult result = parse(reply.json());
                if (result == null || result.confidence() == null) {
                    continue; // 판정 불성립 → fail-open
                }
                model = reply.model();

                // toxic 불리언은 모델마다 들쭉날쭉하므로 category 로 판정한다.
                String category = result.category() == null
                        ? "normal" : result.category().trim().toLowerCase(Locale.ROOT);
                double conf = result.confidence();
                String action = decideAction(category, conf, blurThreshold);

                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("url", url);
                entry.put("category", category);
                entry.put("confidence", conf);
                entry.put("action", action);
                perImage.add(entry);

                if ("hide".equals(action)) {
                    hide = true;          // abuse 고신뢰 → 글 숨김(하드)
                } else if ("blur".equals(action)) {
                    flagged.add(url);     // ad/spam/pii 또는 중신뢰 abuse → 블러(소프트)
                }
            }

            Map<String, Object> resultObj = new LinkedHashMap<>();
            resultObj.put("images", perImage);
            resultObj.put("flagged", flagged);
            resultObj.put("hidden", hide);
            resultObj.put("blurThreshold", blurThreshold);
            resultObj.put("abuseHideThreshold", ABUSE_HIDE_MIN_CONFIDENCE);
            aiResultMapper.complete(postId, AiTaskType.IMAGE_MODERATION,
                    objectMapper.writeValueAsString(resultObj), model);

            // 하드 액션: abuse 고신뢰 이미지 → 글 숨김(텍스트 검열과 동일 경로) + 작성자 알림 + 누적 제재.
            if (hide) {
                int updated = postMapper.hideIfPublished(postId);
                if (updated > 0) {
                    log.warn("이미지 검열 글 숨김: postId={} (abuse 고신뢰 이미지)", postId);
                    sendImageHiddenNotification(post);
                    try {
                        userSanctionService.sanctionIfNeeded(post.getUserId());
                    } catch (Exception e) {
                        log.error("이미지 검열 자동 제재 실패: userId={}", post.getUserId(), e);
                    }
                }
            } else if (!flagged.isEmpty()) {
                log.warn("이미지 검열: postId={} — {}장 중 {}장 블러", postId, perImage.size(), flagged.size());
                sendImageBlurNotification(post, flagged.size());
                // 블러 누적(소프트 스트라이크) 제재 판정 (best-effort: 실패해도 검열 결과엔 영향 없음)
                try {
                    userSanctionService.sanctionIfNeededForBlur(post.getUserId());
                } catch (Exception e) {
                    log.error("이미지 블러 누적 제재 처리 실패: userId={}", post.getUserId(), e);
                }
            } else {
                log.info("이미지 검열 완료: postId={}, 판정={}장", postId, perImage.size());
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.length() > 500) {
                msg = msg.substring(0, 500);
            }
            aiResultMapper.fail(postId, AiTaskType.IMAGE_MODERATION, msg);
            log.error("이미지 검열 실패: postId={}", postId, e);
        }
    }

    /** 본문 HTML 에서 중복 제거된 이미지 URL 목록을 추출한다. */
    private List<String> extractImageUrls(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        Matcher matcher = IMG_SRC.matcher(html);
        while (matcher.find()) {
            String src = matcher.group(1).trim();
            if (!src.isEmpty() && !urls.contains(src)) {
                urls.add(src);
            }
        }
        return urls;
    }

    /** 원격(http/https) 이미지를 내려받아 base64 로 감싼다. 실패/비이미지/과대 크기·상대경로면 null(fail-open). */
    private ModerationImage fetch(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return null; // 원격 이미지만 판정(상대/기타 스킴은 건너뜀)
        }
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            String contentType = response.headers().firstValue("content-type")
                    .map(v -> v.split(";")[0].trim().toLowerCase(Locale.ROOT))
                    .orElse("image/jpeg");
            if (!contentType.startsWith("image/")) {
                return null;
            }
            byte[] bytes = response.body();
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                return null;
            }
            return new ModerationImage(Base64.getEncoder().encodeToString(bytes), contentType);
        } catch (Exception e) {
            log.warn("이미지 검열 다운로드 실패 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    private ModerationResult parse(String json) {
        try {
            return objectMapper.readValue(json, ModerationResult.class);
        } catch (Exception e) {
            log.warn("이미지 검열 응답 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 카테고리·확신도 → 액션 매핑 (도메인 검열 정책).
     * <ul>
     *   <li>abuse(음란·불법·폭력) + 고신뢰(≥{@value #ABUSE_HIDE_MIN_CONFIDENCE}) → {@code hide}: 글 숨김(하드).
     *       법적 위험이 큰 유해물은 클릭하면 보이는 블러가 아니라 하드 차단한다.</li>
     *   <li>그 외 위반(ad/spam/pii, 또는 중신뢰 abuse) + ≥ blurThreshold → {@code blur}: 소프트 블러+클릭하여 보기.</li>
     *   <li>normal 또는 저신뢰 → {@code allow}.</li>
     * </ul>
     */
    private String decideAction(String category, double confidence, double blurThreshold) {
        if ("normal".equals(category)) {
            return "allow";
        }
        if ("abuse".equals(category)) {
            if (confidence >= ABUSE_HIDE_MIN_CONFIDENCE) {
                return "hide";
            }
            return confidence >= blurThreshold ? "blur" : "allow";
        }
        // ad / spam / pii 및 기타 비-normal → 소프트 블러(위험 낮음, 되돌릴 수 있는 조치)
        return confidence >= blurThreshold ? "blur" : "allow";
    }

    /** 이미지 검열로 글이 숨겨질 때 작성자에게 알림. 텍스트 검열의 sendHiddenNotification 동형(문구만 이미지용). */
    private void sendImageHiddenNotification(CommunityPost post) {
        String title = post.getTitle() == null ? "" : post.getTitle();
        if (title.length() > 30) {
            title = title.substring(0, 30);
        }
        Notification noti = Notification.builder()
                .userId(post.getUserId())
                .type("POST_HIDDEN")
                .targetType("POST")
                .targetId(post.getId())
                .title("게시글이 커뮤니티 가이드라인 검토 대기 상태로 전환되었습니다")
                .message("'" + title + "' 게시글의 첨부 이미지가 자동 검수에서 유해로 판정되어 검토 대기 상태로 전환되었습니다. "
                        + "관리자 검토 후 복원되거나 삭제될 수 있습니다.")
                .link("/community?view=guidelines")
                .build();
        notificationService.notify(noti);
    }

    /** 이미지가 블러 처리될 때 작성자에게 알림. 글은 그대로 노출되지만 특정 이미지가 가려졌음을 알린다. */
    private void sendImageBlurNotification(CommunityPost post, int count) {
        String title = post.getTitle() == null ? "" : post.getTitle();
        if (title.length() > 30) {
            title = title.substring(0, 30);
        }
        Notification noti = Notification.builder()
                .userId(post.getUserId())
                .type("POST_IMAGE_BLURRED")
                .targetType("POST")
                .targetId(post.getId())
                .title("게시글의 이미지가 블러 처리되었습니다")
                .message("'" + title + "' 게시글의 첨부 이미지 " + count + "장이 자동 검수에서 민감 콘텐츠로 판정되어 "
                        + "블러 처리되었습니다. 다른 사용자에게는 클릭해야 보입니다. 관리자 검토 후 조정될 수 있습니다.")
                .link("/community/posts/" + post.getId())
                .build();
        notificationService.notify(noti);
    }
}
