package com.careertuner.ai.autoprep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

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
    private final FileService fileService = mock(FileService.class);
    private final AutoPrepAttachmentLoader loader = new AutoPrepAttachmentLoader(userMapper, fileService);

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

    private void stubFreeUser() {
        User user = mock(User.class);
        when(user.getPlan()).thenReturn("FREE");
        when(userMapper.findById(USER_ID)).thenReturn(user);
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
