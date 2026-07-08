package com.careertuner.correction.ai;

import java.util.ArrayList;
import java.util.List;

final class CorrectionParagraphPreserver {

    private CorrectionParagraphPreserver() {
    }

    static String restore(String originalText, String correctedText, int maxChars) {
        int targetParagraphs = paragraphCount(originalText);
        if (targetParagraphs <= 1 || paragraphCount(correctedText) >= targetParagraphs) {
            return correctedText;
        }

        String[] sentences = correctedText.strip().split("(?<=[.!?])\\s+");
        if (sentences.length < targetParagraphs) {
            return correctedText;
        }

        int baseSize = sentences.length / targetParagraphs;
        int remainder = sentences.length % targetParagraphs;
        List<String> paragraphs = new ArrayList<>(targetParagraphs);
        int offset = 0;
        for (int paragraph = 0; paragraph < targetParagraphs; paragraph++) {
            int size = baseSize + (paragraph < remainder ? 1 : 0);
            paragraphs.add(String.join(" ", List.of(sentences).subList(offset, offset + size)));
            offset += size;
        }

        String restored = String.join("\n\n", paragraphs);
        return restored.length() <= maxChars ? restored : correctedText;
    }

    static int paragraphCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("(?:\\r?\\n)\\s*(?:\\r?\\n)+").length;
    }
}
