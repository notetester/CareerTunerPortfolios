package com.careertuner.jobposting.service;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import com.careertuner.applicationcase.service.OpenAiResponsesClient;
import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.jobposting.service.JobPostingFileStorage.StoredJobPostingFile;

@Service
public class JobPostingTextExtractor {

    private static final int MAX_EXTRACTED_TEXT_LENGTH = 120_000;

    private final OpenAiResponsesClient openAiClient;

    public JobPostingTextExtractor(OpenAiResponsesClient openAiClient) {
        this.openAiClient = openAiClient;
    }

    public ExtractedPosting extractFile(StoredJobPostingFile file) {
        if ("PDF".equals(file.sourceType())) {
            String text = extractTextPdf(file);
            OpenAiResponsesClient.Usage usage = null;
            if (text.isBlank()) {
                OpenAiResponsesClient.TextPayload payload = openAiClient.extractPdfText(file.originalFilename(), file.bytes());
                text = payload.text();
                usage = payload.usage();
            }
            return new ExtractedPosting(file.sourceType(), file.fileReference(), null, limit(text), usage);
        }

        OpenAiResponsesClient.TextPayload payload = openAiClient.extractImageText(file.contentType(), file.bytes());
        return new ExtractedPosting(file.sourceType(), file.fileReference(), null, limit(payload.text()), payload.usage());
    }

    public ExtractedPosting extractUrl(String url) {
        String normalizedUrl = normalizeHttpUrl(url);
        try {
            Document document = Jsoup.connect(normalizedUrl)
                    .userAgent("CareerTuner/1.0")
                    .timeout(5000)
                    .maxBodySize(1_000_000)
                    .followRedirects(true)
                    .get();
            document.select("script, style, noscript, svg").remove();
            String title = document.title() == null ? "" : document.title().trim();
            String body = document.body() == null ? "" : document.body().text().trim();
            String text = (title.isBlank() ? body : title + "\n\n" + body).trim();
            if (text.isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "URL에서 공고문 텍스트를 추출하지 못했습니다.");
            }
            return new ExtractedPosting("URL", normalizedUrl, normalizedUrl, limit(text), null);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "URL을 불러오지 못했습니다.");
        }
    }

    private String extractTextPdf(StoredJobPostingFile file) {
        try (PDDocument document = Loader.loadPDF(file.bytes())) {
            return new PDFTextStripper().getText(document).trim();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "PDF 텍스트를 추출하지 못했습니다.");
        }
    }

    private String normalizeHttpUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공고 URL을 입력해 주세요.");
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "http 또는 https URL만 사용할 수 있습니다.");
            }
            return uri.toString();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공고 URL 형식이 올바르지 않습니다.");
        }
    }

    private String limit(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= MAX_EXTRACTED_TEXT_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_EXTRACTED_TEXT_LENGTH);
    }

    public record ExtractedPosting(
            String sourceType,
            String uploadedFileUrl,
            String originalText,
            String extractedText,
            OpenAiResponsesClient.Usage usage
    ) {
    }
}
