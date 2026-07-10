package com.careertuner.common.text;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/**
 * 공용 문서 텍스트 추출기 (PDF / DOCX / TXT / MD).
 * 성공 시 text 가 non-null, 실패 시 reason 이 원인 enum 을 담는다.
 * AutoPrep 첨부 로더와 프로필 import 가 공유한다.
 */
@Component
public class DocumentTextExtractor {

    public record Extraction(String text, Failure reason) {
        public boolean isSuccess() {
            return text != null;
        }

        public static Extraction ok(String text) {
            return new Extraction(text, null);
        }

        public static Extraction fail(Failure reason) {
            return new Extraction(null, reason);
        }
    }

    public enum Failure {
        NO_TEXT_LAYER,
        CORRUPTED,
        UNSUPPORTED_FORMAT,
        EMPTY
    }

    /**
     * @param bytes        파일 바이트 (null/0 → EMPTY)
     * @param contentType  MIME (nullable)
     * @param originalName 원본 파일명 (확장자 폴백용, nullable)
     */
    public Extraction extract(byte[] bytes, String contentType, String originalName) {
        if (bytes == null || bytes.length == 0) {
            return Extraction.fail(Failure.EMPTY);
        }

        if (isPlainText(contentType, originalName)) {
            return extractPlainText(bytes);
        }
        if (isPdf(contentType, originalName)) {
            return extractPdf(bytes);
        }
        if (isDocx(contentType, originalName)) {
            return extractDocx(bytes);
        }
        return Extraction.fail(Failure.UNSUPPORTED_FORMAT);
    }

    private Extraction extractPlainText(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return Extraction.fail(Failure.EMPTY);
        }
        return Extraction.ok(text);
    }

    private Extraction extractPdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document).trim();
            if (text.isEmpty()) {
                // 스캔/이미지 PDF — 문서는 열리지만 텍스트 레이어 없음
                return Extraction.fail(Failure.NO_TEXT_LAYER);
            }
            return Extraction.ok(text);
        } catch (IOException | RuntimeException ex) {
            return Extraction.fail(Failure.CORRUPTED);
        }
    }

    private Extraction extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            String text = extractor.getText();
            if (text == null || text.trim().isEmpty()) {
                return Extraction.fail(Failure.EMPTY);
            }
            return Extraction.ok(text.trim());
        } catch (IOException | RuntimeException ex) {
            return Extraction.fail(Failure.CORRUPTED);
        }
    }

    private static boolean isPlainText(String contentType, String originalName) {
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.startsWith("text/") || ct.contains("markdown")) {
                return true;
            }
        }
        if (originalName == null) {
            return false;
        }
        String lower = originalName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    private static boolean isPdf(String contentType, String originalName) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf")) {
            return true;
        }
        return originalName != null && originalName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private static boolean isDocx(String contentType, String originalName) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("wordprocessingml")) {
            return true;
        }
        return originalName != null && originalName.toLowerCase(Locale.ROOT).endsWith(".docx");
    }
}
