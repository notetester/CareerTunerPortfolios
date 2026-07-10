package com.careertuner.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import com.careertuner.common.text.DocumentTextExtractor.Extraction;
import com.careertuner.common.text.DocumentTextExtractor.Failure;

/**
 * 공용 추출기 게이트: ASCII PDF + Hangul DOCX 성공, 실패 enum 4종.
 * PDF 픽스처는 HELVETICA(한글 글리프 없음) 제약이 있어 ASCII 만 사용.
 */
class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void extractsAsciiTextPdf() throws Exception {
        byte[] pdf = asciiPdf("Hello CareerTuner resume text");

        Extraction result = extractor.extract(pdf, "application/pdf", "resume.pdf");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.text()).contains("Hello CareerTuner resume text");
        assertThat(result.reason()).isNull();
    }

    @Test
    void extractsHangulFromDocx() throws Exception {
        byte[] docx = docxBytes("저는 백엔드 개발자를 지망합니다. Java Spring");

        Extraction result = extractor.extract(
                docx,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "이력서.docx");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.text()).contains("백엔드 개발자를 지망합니다");
        assertThat(result.text()).contains("Java Spring");
    }

    @Test
    void extractsPlainTextAndMarkdown() {
        Extraction txt = extractor.extract(
                "plain resume body".getBytes(StandardCharsets.UTF_8), "text/plain", "a.txt");
        Extraction md = extractor.extract(
                "# Title\nbody".getBytes(StandardCharsets.UTF_8), null, "notes.md");

        assertThat(txt.text()).isEqualTo("plain resume body");
        assertThat(md.text()).contains("Title");
    }

    @Test
    void scanPdfWithoutTextLayer_returnsNoTextLayer() throws Exception {
        // 빈 페이지 PDF — 로드는 되지만 stripper 결과가 공백
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            Extraction result = extractor.extract(out.toByteArray(), "application/pdf", "scan.pdf");
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.reason()).isEqualTo(Failure.NO_TEXT_LAYER);
        }
    }

    @Test
    void corruptPdf_returnsCorrupted() {
        Extraction result = extractor.extract(new byte[] { 1, 2, 3, 4 }, "application/pdf", "bad.pdf");
        assertThat(result.reason()).isEqualTo(Failure.CORRUPTED);
    }

    @Test
    void corruptDocx_returnsCorrupted() {
        Extraction result = extractor.extract(
                new byte[] { 1, 2, 3 },
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "broken.docx");
        assertThat(result.reason()).isEqualTo(Failure.CORRUPTED);
    }

    @Test
    void legacyDoc_returnsUnsupportedFormat() {
        Extraction result = extractor.extract(
                new byte[] { 5, 6, 7 }, "application/msword", "cover.doc");
        assertThat(result.reason()).isEqualTo(Failure.UNSUPPORTED_FORMAT);
    }

    @Test
    void emptyBytes_returnsEmpty() {
        Extraction result = extractor.extract(new byte[0], "text/plain", "empty.txt");
        assertThat(result.reason()).isEqualTo(Failure.EMPTY);
    }

    @Test
    void whitespaceOnlyText_returnsEmpty() {
        Extraction result = extractor.extract(
                "   \n\t  ".getBytes(StandardCharsets.UTF_8), "text/plain", "blank.txt");
        assertThat(result.reason()).isEqualTo(Failure.EMPTY);
    }

    private static byte[] asciiPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
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
