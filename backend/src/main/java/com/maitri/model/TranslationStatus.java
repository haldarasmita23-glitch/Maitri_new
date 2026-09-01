package com.maitri.model;

/**
 * TranslationStatus — Represents the translation outcome for a chat message.
 */
public enum TranslationStatus {
    /** The message was successfully translated into the receiver's preferred language. */
    TRANSLATED,

    /** Translation was not needed (e.g. sender and receiver share the same language). */
    NOT_REQUIRED,

    /** Translation was attempted but failed (e.g. network/provider error). Fallback is active. */
    FAILED,

    /** Translation provider is unavailable or language pair is unsupported. */
    UNAVAILABLE
}
