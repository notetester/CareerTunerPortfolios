package com.careertuner.ai.autoprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import com.careertuner.common.text.DocumentTextExtractor;
import com.careertuner.billing.domain.SubscriptionBenefitPolicy;
import com.careertuner.billing.service.BillingPolicyService;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.file.domain.FileAsset;
import com.careertuner.file.service.FileService;
import com.careertuner.user.domain.User;
import com.careertuner.user.mapper.UserMapper;

/** .docx 첨부 본문 추출 — WRITE 파트가 소비할 text 를 실제 docx 바이트에서 뽑아내는지 검증한다. */
class AutoPrepAttachmentLoaderDocxTest {

    private static final String DOCX_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final Long USER_ID = 9L;

    private final UserMapper userMapper = mock(UserMapper.class);
    private final BillingPolicyService billingPolicyService = mock(BillingPolicyService.class);
    private final FileService fileService = mock(FileService.class);
    private final DocumentTextExtractor documentTextExtractor = new DocumentTextExtractor();
    private final AutoPrepAttachmentLoader loader =
            new AutoPrepAttachmentLoader(userMapper, billingPolicyService, fileService, documentTextExtractor);

    @Test
    void load_extractsTextFromDocx() throws Exception {
        stubFreeUser();
        stubDownload(1L, "자소서.docx", DOCX_TYPE, docxBytes("저는 백엔드 개발자를 지망합니다."));

        List<PrepAttachment> result = loader.load(USER_ID, List.of(1L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hasText()).isTrue();
        assertThat(result.get(0).text()).contains("백엔드 개발자를 지망합니다");
    }

    /** contentType 이 비어 와도(브라우저 편차) 확장자로 docx 를 잡아야 한다 — isPdf 와 같은 관용 규칙. */
    @Test
    void load_detectsDocxByExtensionWhenContentTypeMissing() throws Exception {
        stubFreeUser();
        stubDownload(2L, "cover.docx", null, docxBytes("확장자만으로 판정한다"));

        List<PrepAttachment> result = loader.load(USER_ID, List.of(2L));

        assertThat(result.get(0).hasText()).isTrue();
    }

    /** 손상/암호화 docx 는 예외를 던지지 않고 text 만 비운다(항상 진행 원칙) — 첨부 메타는 남는다. */
    @Test
    void load_keepsAttachmentWhenDocxIsCorrupt() {
        stubFreeUser();
        stubDownload(3L, "broken.docx", DOCX_TYPE, new byte[] { 1, 2, 3 });

        List<PrepAttachment> result = loader.load(USER_ID, List.of(3L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hasText()).isFalse();
    }

    /** 구형 .doc 은 파서가 없다 — docx 로 오인해 깨지지 않고 조용히 text=null 이어야 한다. */
    @Test
    void load_ignoresLegacyDocBinary() {
        stubFreeUser();
        stubDownload(4L, "cover.doc", "application/msword", new byte[] { 5, 6, 7 });

        List<PrepAttachment> result = loader.load(USER_ID, List.of(4L));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hasText()).isFalse();
    }

    @Test
    void load_usesCurrentBillingPolicyQuantityInsteadOfHardcodedPlanBranch() throws Exception {
        User user = mock(User.class);
        when(user.getPlan()).thenReturn("PRO");
        when(userMapper.findById(USER_ID)).thenReturn(user);
        when(billingPolicyService.activeBenefitPolicy("PRO", "AUTOPREP_ATTACHMENT", null))
                .thenReturn(policy(2));
        stubDownload(10L, "a.docx", DOCX_TYPE, docxBytes("a"));
        stubDownload(11L, "b.docx", DOCX_TYPE, docxBytes("b"));
        stubDownload(12L, "c.docx", DOCX_TYPE, docxBytes("c"));

        assertThat(loader.load(USER_ID, List.of(10L, 11L, 12L)))
                .extracting(PrepAttachment::fileId)
                .containsExactly(10L, 11L);
    }

    @Test
    void loadForRequest_appliesOneCombinedLimitToPostingAndResumeAttachments() throws Exception {
        User user = mock(User.class);
        when(user.getPlan()).thenReturn("PRO");
        when(userMapper.findById(USER_ID)).thenReturn(user);
        when(billingPolicyService.activeBenefitPolicy("PRO", "AUTOPREP_ATTACHMENT", null))
                .thenReturn(policy(2));
        stubDownload(20L, "jd.txt", "text/plain", "채용 공고".getBytes());
        stubDownload(21L, "resume-a.txt", "text/plain", "자소서 A".getBytes());
        stubDownload(22L, "resume-b.txt", "text/plain", "자소서 B".getBytes());

        List<PrepAttachment> posting = loader.loadForRequest(
                USER_ID, List.of(20L), List.of(20L), List.of(21L, 22L));
        List<PrepAttachment> resumes = loader.loadForRequest(
                USER_ID, List.of(21L, 22L), List.of(20L), List.of(21L, 22L));

        assertThat(posting).extracting(PrepAttachment::fileId).containsExactly(20L);
        assertThat(resumes).extracting(PrepAttachment::fileId).containsExactly(21L);
        verify(fileService, never()).download(USER_ID, 22L);
    }

    @Test
    void validateRequestLimit_rejectsFreePostingPlusResumeAsOneRequest() {
        stubFreeUser();

        assertThatThrownBy(() -> loader.validateRequestLimit(USER_ID, List.of(20L), List.of(21L)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
                    assertThat(ex.getMessage()).contains("합해 최대 1개");
                });
    }

    @Test
    void validateRequestLimit_countsMultipartBinaryPostingBeforeCaseCreation() {
        stubFreeUser();

        assertThatThrownBy(() -> loader.validateRequestLimit(
                USER_ID, null, List.of(21L), 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("공고와 자소서를 합해 최대 1개");
    }

    @Test
    void validateRequestLimit_allowsFiveDistinctFilesForMatchingProPolicy() {
        User user = mock(User.class);
        when(user.getPlan()).thenReturn("PRO");
        when(userMapper.findById(USER_ID)).thenReturn(user);
        when(billingPolicyService.activeBenefitPolicy("PRO", "AUTOPREP_ATTACHMENT", null))
                .thenReturn(policy(5));

        assertThatCode(() -> loader.validateRequestLimit(
                USER_ID, List.of(20L), List.of(21L, 22L, 23L, 24L)))
                .doesNotThrowAnyException();
    }

    @Test
    void validateRequestLimit_countsDuplicateIdOnlyOnceAcrossRoles() {
        stubFreeUser();

        assertThatCode(() -> loader.validateRequestLimit(USER_ID, List.of(20L), List.of(20L)))
                .doesNotThrowAnyException();
    }

    private void stubFreeUser() {
        User user = mock(User.class);
        when(user.getPlan()).thenReturn("FREE");
        when(userMapper.findById(USER_ID)).thenReturn(user);
        when(billingPolicyService.activeBenefitPolicy("FREE", "AUTOPREP_ATTACHMENT", null))
                .thenReturn(policy(1));
    }

    private static SubscriptionBenefitPolicy policy(int quantity) {
        SubscriptionBenefitPolicy policy = new SubscriptionBenefitPolicy();
        policy.setQuantity(quantity);
        policy.setActive(true);
        return policy;
    }

    private void stubDownload(Long fileId, String name, String contentType, byte[] bytes) {
        FileAsset asset = mock(FileAsset.class);
        when(asset.getId()).thenReturn(fileId);
        when(asset.getOriginalName()).thenReturn(name);
        when(asset.getContentType()).thenReturn(contentType);
        when(fileService.download(USER_ID, fileId)).thenReturn(new FileService.Download(asset, bytes));
    }

    private static byte[] docxBytes(String paragraph) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(paragraph);
            document.write(out);
            return out.toByteArray();
        }
    }
}
