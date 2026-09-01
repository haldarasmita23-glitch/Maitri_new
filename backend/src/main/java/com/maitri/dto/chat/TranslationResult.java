package com.maitri.dto.chat;

import com.maitri.model.TranslationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TranslationResult — Internal DTO representing the outcome of a translation operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslationResult {
    private String originalText;
    private String translatedText;
    private String sourceLanguage;
    private String targetLanguage;
    private TranslationStatus status;
}
