package com.maitri.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.chat.TranslationResult;
import com.maitri.model.TranslationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TranslationService Unit Tests")
class TranslationServiceTest {

    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        translationService = new TranslationService(new ObjectMapper());
    }

    @Nested
    @DisplayName("Language Detection Tests")
    class DetectionTests {

        @Test
        @DisplayName("Detects Kannada script from Unicode characters")
        void detectsKannada() {
            String lang = translationService.detectLanguage("ನಿಮ್ಮ ಅಂಗಡಿ ಇಂದು ತೆರೆದಿದೆಯೇ?", "en");
            assertThat(lang).isEqualTo("kn");
        }

        @Test
        @DisplayName("Detects Hindi (Devanagari) script from Unicode characters")
        void detectsHindi() {
            String lang = translationService.detectLanguage("क्या आपकी दुकान आज खुली है?", "en");
            assertThat(lang).isEqualTo("hi");
        }

        @Test
        @DisplayName("Detects English/Latin script text")
        void detectsEnglish() {
            String lang = translationService.detectLanguage("Is your shop open today?", "en");
            assertThat(lang).isEqualTo("en");
        }

        @Test
        @DisplayName("Uses sender hint if text is empty or blank")
        void fallbackOnBlank() {
            assertThat(translationService.detectLanguage("", "kn")).isEqualTo("kn");
            assertThat(translationService.detectLanguage("   ", "hi")).isEqualTo("hi");
            assertThat(translationService.detectLanguage(null, "kn")).isEqualTo("kn");
        }
    }

    @Nested
    @DisplayName("Multilingual Translation Tests")
    class TranslationTests {

        @Test
        @DisplayName("1. English to Kannada translation")
        void englishToKannada() {
            TranslationResult result = translationService.translate("Your order is ready", "kn", "en");
            assertThat(result.getStatus()).isEqualTo(TranslationStatus.TRANSLATED);
            assertThat(result.getSourceLanguage()).isEqualTo("en");
            assertThat(result.getTargetLanguage()).isEqualTo("kn");
            assertThat(result.getTranslatedText()).isEqualTo("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ");
        }

        @Test
        @DisplayName("2. English to Hindi translation")
        void englishToHindi() {
            TranslationResult result = translationService.translate("Your order is ready", "hi", "en");
            assertThat(result.getStatus()).isEqualTo(TranslationStatus.TRANSLATED);
            assertThat(result.getSourceLanguage()).isEqualTo("en");
            assertThat(result.getTargetLanguage()).isEqualTo("hi");
            assertThat(result.getTranslatedText()).isEqualTo("आपका ऑर्डर तैयार है");
        }

        @Test
        @DisplayName("3. Kannada to English translation")
        void kannadaToEnglish() {
            TranslationResult result = translationService.translate("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ", "en", "kn");
            assertThat(result.getStatus()).isEqualTo(TranslationStatus.TRANSLATED);
            assertThat(result.getSourceLanguage()).isEqualTo("kn");
            assertThat(result.getTargetLanguage()).isEqualTo("en");
            assertThat(result.getTranslatedText()).isEqualTo("your order is ready");
        }

        @Test
        @DisplayName("4. Kannada to Hindi translation")
        void kannadaToHindi() {
            TranslationResult result = translationService.translate("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ", "hi", "kn");
            assertThat(result.getStatus()).isEqualTo(TranslationStatus.TRANSLATED);
            assertThat(result.getSourceLanguage()).isEqualTo("kn");
            assertThat(result.getTargetLanguage()).isEqualTo("hi");
            assertThat(result.getTranslatedText()).isEqualTo("आपका ऑर्डर तैयार है");
        }

        @Test
        @DisplayName("5. Hindi to English translation")
        void hindiToEnglish() {
            TranslationResult result = translationService.translate("क्या आपकी दुकान आज खुली है?", "en", "hi");
            assertThat(result.getStatus()).isEqualTo(TranslationStatus.TRANSLATED);
            assertThat(result.getSourceLanguage()).isEqualTo("hi");
            assertThat(result.getTargetLanguage()).isEqualTo("en");
            assertThat(result.getTranslatedText()).isEqualTo("is your shop open today?");
        }

        @Test
        @DisplayName("6. Hindi to Kannada translation")
        void hindiToKannada() {
            TranslationResult result = translationService.translate("क्या आपकी दुकान आज खुली है?", "kn", "hi");
            assertThat(result.getStatus()).isEqualTo(TranslationStatus.TRANSLATED);
            assertThat(result.getSourceLanguage()).isEqualTo("hi");
            assertThat(result.getTargetLanguage()).isEqualTo("kn");
            assertThat(result.getTranslatedText()).isEqualTo("ನಿಮ್ಮ ಅಂಗಡಿ ಇಂದು ತೆರೆದಿದೆಯೇ?");
        }

        @Test
        @DisplayName("7. Same language message returns NOT_REQUIRED without translating")
        void sameLanguageNoTranslation() {
            TranslationResult result = translationService.translate("Hello, how are you?", "en", "en");
            assertThat(result.getStatus()).isEqualTo(TranslationStatus.NOT_REQUIRED);
            assertThat(result.getTranslatedText()).isEqualTo("Hello, how are you?");
            assertThat(result.getSourceLanguage()).isEqualTo("en");
            assertThat(result.getTargetLanguage()).isEqualTo("en");

            TranslationResult knResult = translationService.translate("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ", "kn", "kn");
            assertThat(knResult.getStatus()).isEqualTo(TranslationStatus.NOT_REQUIRED);
            assertThat(knResult.getTranslatedText()).isEqualTo("ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ");
        }

        @Test
        @DisplayName("8. Empty or null message returns NOT_REQUIRED")
        void emptyOrNullMessage() {
            TranslationResult resultNull = translationService.translate(null, "kn", "en");
            assertThat(resultNull.getStatus()).isEqualTo(TranslationStatus.NOT_REQUIRED);

            TranslationResult resultEmpty = translationService.translate("   ", "kn", "en");
            assertThat(resultEmpty.getStatus()).isEqualTo(TranslationStatus.NOT_REQUIRED);
        }

        @Test
        @DisplayName("9. Original message is always preserved in result")
        void originalMessagePreserved() {
            String raw = "Unique unlisted product inquiry #12345";
            TranslationResult result = translationService.translate(raw, "kn", "en");
            assertThat(result.getOriginalText()).isEqualTo(raw);
            assertThat(result.getTranslatedText()).isNotNull();
        }
    }
}
