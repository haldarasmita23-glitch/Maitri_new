package com.maitri.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maitri.dto.chat.TranslationResult;
import com.maitri.model.TranslationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/**
 * TranslationService — Real-time multilingual translation abstraction.
 *
 * ─── RESPONSIBILITIES ────────────────────────────────────────────────────────
 *   1. Automatic script-based source language detection (Kannada, Hindi, English).
 *   2. Synchronous translation across en ↔ hi ↔ kn language pairs.
 *   3. Multi-tier translation pipeline:
 *        - Tier 1: Local hyperlocal commerce phrasebook (offline / deterministic).
 *        - Tier 2: Real REST Translation Provider (MyMemory / LibreTranslate / Google).
 *        - Tier 3: Resilient fallback (preserves original text with FAILED/UNAVAILABLE status).
 *   4. Zero external API credentials required for base operations while supporting
 *      configurable live translation endpoints via environment variables.
 */
@Service
@Slf4j
public class TranslationService {

    private static final Pattern KANNADA_PATTERN = Pattern.compile("[\\u0C80-\\u0CFF]");
    private static final Pattern DEVANAGARI_PATTERN = Pattern.compile("[\\u0900-\\u097F]");

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("en", "hi", "kn");

    @Value("${maitri.translation.provider:auto}")
    private String provider;

    @Value("${maitri.translation.api-key:${TRANSLATION_API_KEY:}}")
    private String apiKey;

    @Value("${maitri.translation.api-url:${TRANSLATION_API_URL:}}")
    private String apiUrl;

    @Value("${maitri.translation.timeout-ms:3000}")
    private int timeoutMs;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /** Hyperlocal Commerce Dictionary mapping normalized phrases across [en, hi, kn]. */
    private static final List<Map<String, String>> PHRASEBOOK = new ArrayList<>();

    static {
        addPhrase("your order is ready", "आपका ऑर्डर तैयार है", "ನಿಮ್ಮ ಆರ್ಡರ್ ಸಿದ್ಧವಾಗಿದೆ");
        addPhrase("is your shop open today?", "क्या आपकी दुकान आज खुली है?", "ನಿಮ್ಮ ಅಂಗಡಿ ಇಂದು ತೆರೆದಿದೆಯೇ?");
        addPhrase("is your shop open today", "क्या आपकी दुकान आज खुली है", "ನಿಮ್ಮ ಅಂಗಡಿ ಇಂದು ತೆರೆದಿದೆಯೇ");
        addPhrase("hello, is your shop open today?", "नमस्ते, क्या आपकी दुकान आज खुली है?", "ನಮಸ್ಕಾರ, ನಿಮ್ಮ ಅಂಗಡಿ ಇಂದು ತೆರೆದಿದೆಯೇ?");
        addPhrase("hello, is your shop open today", "नमस्ते, क्या आपकी दुकान आज खुली है", "ನಮಸ್ಕಾರ, ನಿಮ್ಮ ಅಂಗಡಿ ಇಂದು ತೆರೆದಿದೆಯೇ");
        addPhrase("when will your shop open?", "आपकी दुकान कब खुलेगी?", "ನಿಮ್ಮ ಅಂಗಡಿ ಯಾವಾಗ ತೆರೆಯುತ್ತದೆ?");
        addPhrase("when will your shop open", "आपकी दुकान कब खुलेगी", "ನಿಮ್ಮ ಅಂಗಡಿ ಯಾವಾಗ ತೆರೆಯುತ್ತದೆ");
        addPhrase("what is the price?", "कीमत क्या है?", "ಬೆಲೆ ಎಷ್ಟು?");
        addPhrase("what is the price", "कीमत क्या है", "ಬೆಲೆ ಎಷ್ಟು");
        addPhrase("is this item in stock?", "क्या यह सामान स्टॉक में है?", "ಈ ವಸ್ತು ಸ್ಟಾಕ್‌ನಲ್ಲಿದೆಯೇ?");
        addPhrase("is this item in stock", "क्या यह सामान स्टॉक में है", "ಈ ವಸ್ತು ಸ್ಟಾಕ್‌ನಲ್ಲಿದೆಯೇ");
        addPhrase("thank you", "धन्यवाद", "ಧನ್ಯವಾದಗಳು");
        addPhrase("thank you, i will collect it soon", "धन्यवाद, मैं इसे जल्द ही ले लूंगा", "ಧನ್ಯವಾದಗಳು, ನಾನು ಅದನ್ನು ಶೀಘ್ರದಲ್ಲೇ ಸಂಗ್ರಹಿಸುತ್ತೇನೆ");
        addPhrase("hello", "नमस्ते", "ನಮಸ್ಕಾರ");
        addPhrase("hi", "नमस्ते", "ನಮಸ್ಕಾರ");
        addPhrase("namaste", "नमस्ते", "ನಮಸ್ಕಾರ");
        addPhrase("hello sir", "नमस्ते सर", "ನಮಸ್ಕಾರ ಸರ್");
        addPhrase("good morning", "शुभ प्रभात", "ಶುಭೋದಯ");
        addPhrase("good evening", "शुभ संध्या", "ಶುಭ ಸಂಜೆ");
        addPhrase("yes, it is available", "हाँ, यह उपलब्ध है", "ಹೌದು, ಇದು ಲಭ್ಯವಿದೆ");
        addPhrase("no, it is out of stock", "नहीं, यह स्टॉक में नहीं है", "ಇಲ್ಲ, ಇದು ಸ್ಟಾಕ್‌ನಲ್ಲಿಲ್ಲ");
        addPhrase("where is your shop located?", "आपकी दुकान कहाँ है?", "ನಿಮ್ಮ ಅಂಗಡಿ ಎಲ್ಲಿದೆ?");
        addPhrase("where is your shop located", "आपकी दुकान कहाँ है", "ನಿಮ್ಮ ಅಂಗಡಿ ಎಲ್ಲಿದೆ");
        addPhrase("can you deliver to peenya?", "क्या आप पीन्या में डिलीवरी कर सकते हैं?", "ನೀವು ಪೀಣ್ಯಕ್ಕೆ ಡೆಲಿವರಿ ಮಾಡಬಹುದೇ?");
        addPhrase("how long will the repair take?", "मरम्मत में कितना समय लगेगा?", "ದುರಸ್ತಿಗೆ ಎಷ್ಟು ಸಮಯ ತೆಗೆದುಕೊಳ್ಳುತ್ತದೆ?");
        addPhrase("how much for tailoring a shirt?", "शर्ट सिलने का कितना लगेगा?", "ಶರ್ಟ್ ಹೊಲಿಯಲು ಎಷ್ಟು?");
        addPhrase("do you do printing and xerox?", "क्या आप प्रिंटिंग और जेरोक्स करते हैं?", "ನೀವು ಪ್ರಿಂಟಿಂಗ್ ಮತ್ತು ಜೆರಾಕ್ಸ್ ಮಾಡುತ್ತೀರಾ?");
        addPhrase("conversation started", "बातचीत शुरू हुई", "ಸಂಭಾಷಣೆ ಪ್ರಾರಂಭವಾಗಿದೆ");
        addPhrase("order received", "ऑर्डर प्राप्त हुआ", "ಆರ್ಡರ್ ಸ್ವೀಕರಿಸಲಾಗಿದೆ");
        addPhrase("payment completed", "भुगतान पूरा हुआ", "ಪಾವತಿ ಪೂರ್ಣಗೊಂಡಿದೆ");
        addPhrase("please come tomorrow", "कृपया कल आएं", "ದಯವಿಟ್ಟು ನಾಳೆ ಬನ್ನಿ");
    }

    private static void addPhrase(String en, String hi, String kn) {
        Map<String, String> map = new HashMap<>();
        map.put("en", en);
        map.put("hi", hi);
        map.put("kn", kn);
        PHRASEBOOK.add(map);
    }

    public TranslationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2500);
        factory.setReadTimeout(2500);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Detects source language using Unicode script detection, with sender's preference as fallback.
     *
     * @param text The text to inspect
     * @param senderHint Preferred language hint of the sender
     * @return "kn", "hi", or "en"
     */
    public String detectLanguage(String text, String senderHint) {
        if (text == null || text.trim().isEmpty()) {
            return normalizeLanguageCode(senderHint);
        }

        if (KANNADA_PATTERN.matcher(text).find()) {
            return "kn";
        }
        if (DEVANAGARI_PATTERN.matcher(text).find()) {
            return "hi";
        }

        // If Latin/ASCII, verify if sender has a valid non-English hint or default to English
        String normalizedHint = normalizeLanguageCode(senderHint);
        if ("en".equals(normalizedHint)) {
            return "en";
        }

        // If text contains only standard ASCII/Latin, it's English
        return "en";
    }

    /**
     * Translates text into targetLanguage safely and synchronously.
     *
     * @param text The raw message text
     * @param targetLanguage Target language code ("en", "hi", "kn")
     * @param senderHint Sender's configured language preference
     * @return TranslationResult containing translated text and status
     */
    public TranslationResult translate(String text, String targetLanguage, String senderHint) {
        if (text == null || text.trim().isEmpty()) {
            return TranslationResult.builder()
                    .originalText(text)
                    .translatedText(text)
                    .sourceLanguage("en")
                    .targetLanguage(normalizeLanguageCode(targetLanguage))
                    .status(TranslationStatus.NOT_REQUIRED)
                    .build();
        }

        String targetLang = normalizeLanguageCode(targetLanguage);
        String sourceLang = detectLanguage(text, senderHint);

        // Same language — no translation needed
        if (sourceLang.equalsIgnoreCase(targetLang)) {
            return TranslationResult.builder()
                    .originalText(text)
                    .translatedText(text)
                    .sourceLanguage(sourceLang)
                    .targetLanguage(targetLang)
                    .status(TranslationStatus.NOT_REQUIRED)
                    .build();
        }

        // If provider explicitly disabled
        if ("disabled".equalsIgnoreCase(provider)) {
            return TranslationResult.builder()
                    .originalText(text)
                    .translatedText(text)
                    .sourceLanguage(sourceLang)
                    .targetLanguage(targetLang)
                    .status(TranslationStatus.UNAVAILABLE)
                    .build();
        }

        // Tier 1: Local Lexicon Lookup
        String dictionaryMatch = lookupDictionary(text, sourceLang, targetLang);
        if (dictionaryMatch != null) {
            log.debug("[Translation] Dictionary match found for '{}': {}", text, dictionaryMatch);
            return TranslationResult.builder()
                    .originalText(text)
                    .translatedText(dictionaryMatch)
                    .sourceLanguage(sourceLang)
                    .targetLanguage(targetLang)
                    .status(TranslationStatus.TRANSLATED)
                    .build();
        }

        // Tier 2: Real REST Translation Provider (e.g. MyMemory or Custom API)
        try {
            String translated = callExternalProvider(text, sourceLang, targetLang);
            if (translated != null && !translated.trim().isEmpty() && !translated.equalsIgnoreCase(text)) {
                return TranslationResult.builder()
                        .originalText(text)
                        .translatedText(translated)
                        .sourceLanguage(sourceLang)
                        .targetLanguage(targetLang)
                        .status(TranslationStatus.TRANSLATED)
                        .build();
            }
        } catch (Exception ex) {
            log.warn("[Translation] External provider translation failed for '{}' ({} -> {}): {}",
                    text, sourceLang, targetLang, ex.getMessage());
        }

        // Tier 3: Resilient Fallback (preserve original text without crashing)
        log.info("[Translation] Fallback active for '{}' ({} -> {})", text, sourceLang, targetLang);
        return TranslationResult.builder()
                .originalText(text)
                .translatedText(text)
                .sourceLanguage(sourceLang)
                .targetLanguage(targetLang)
                .status(TranslationStatus.FAILED)
                .build();
    }

    /**
     * Checks the local phrasebook for exact or normalized matches.
     */
    private String lookupDictionary(String text, String sourceLang, String targetLang) {
        String normalized = text.trim().toLowerCase().replaceAll("[!?,.]+$", "").trim();

        for (Map<String, String> entry : PHRASEBOOK) {
            String srcVal = entry.get(sourceLang);
            if (srcVal != null) {
                String srcNorm = srcVal.trim().toLowerCase().replaceAll("[!?,.]+$", "").trim();
                if (srcNorm.equalsIgnoreCase(normalized) || srcVal.equalsIgnoreCase(text.trim())) {
                    return entry.get(targetLang);
                }
            }
        }
        return null;
    }

    /**
     * Calls external translation API provider.
     */
    private String callExternalProvider(String text, String sourceLang, String targetLang) {
        if ("google".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank()) {
            return callGoogleTranslate(text, sourceLang, targetLang);
        }

        // Default / Auto / MyMemory Free Endpoint
        return callMyMemoryTranslate(text, sourceLang, targetLang);
    }

    private String callMyMemoryTranslate(String text, String sourceLang, String targetLang) {
        String pair = sourceLang + "|" + targetLang;
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("https://api.mymemory.translated.net/get")
                .queryParam("q", text)
                .queryParam("langpair", pair);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.queryParam("key", apiKey);
        }

        URI uri = builder.build().encode().toUri();
        String response = restTemplate.getForObject(uri, String.class);

        if (response != null) {
            try {
                JsonNode root = objectMapper.readTree(response);
                JsonNode responseData = root.path("responseData");
                if (!responseData.isMissingNode()) {
                    String translated = responseData.path("translatedText").asText(null);
                    if (translated != null && !translated.startsWith("MYMEMORY WARNING:") && !translated.isBlank()) {
                        return translated;
                    }
                }
            } catch (Exception e) {
                log.debug("[Translation] Failed to parse MyMemory response: {}", e.getMessage());
            }
        }
        return null;
    }

    private String callGoogleTranslate(String text, String sourceLang, String targetLang) {
        String url = apiUrl != null && !apiUrl.isBlank()
                ? apiUrl
                : "https://translation.googleapis.com/language/translate/v2";

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("key", apiKey)
                .queryParam("q", text)
                .queryParam("source", sourceLang)
                .queryParam("target", targetLang)
                .queryParam("format", "text");

        String response = restTemplate.postForObject(builder.build().encode().toUri(), null, String.class);
        if (response != null) {
            try {
                JsonNode root = objectMapper.readTree(response);
                JsonNode translations = root.path("data").path("translations");
                if (translations.isArray() && translations.size() > 0) {
                    return translations.get(0).path("translatedText").asText(null);
                }
            } catch (Exception e) {
                log.debug("[Translation] Failed to parse Google response: {}", e.getMessage());
            }
        }
        return null;
    }

    private String normalizeLanguageCode(String lang) {
        if (lang == null || lang.trim().isEmpty()) {
            return "en";
        }
        String clean = lang.trim().toLowerCase();
        return SUPPORTED_LANGUAGES.contains(clean) ? clean : "en";
    }
}
