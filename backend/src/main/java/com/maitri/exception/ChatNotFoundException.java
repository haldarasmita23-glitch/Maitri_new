package com.maitri.exception;

/**
 * ChatNotFoundException — thrown when a chat message or conversation cannot be found
 * for the authenticated account (Phase 11).
 *
 * ─── WHEN THIS IS THROWN ─────────────────────────────────────────────────────
 *   - GET /api/chats/{chatId} for an unknown id, or a conversation that doesn't
 *     involve the authenticated account
 *   - PUT /api/chats/{chatId}/read for an unknown conversation
 *
 * ─── HTTP STATUS ─────────────────────────────────────────────────────────────
 *   Returns HTTP 404 Not Found (handled by GlobalExceptionHandler)
 *
 * ─── SECURITY CONSIDERATION ──────────────────────────────────────────────────
 *   Operations are always scoped to the authenticated account. A 404 (rather
 *   than a 403) ensures no information is revealed about the existence of
 *   another account's conversations.
 *
 * ─── FRONTEND HANDLING ───────────────────────────────────────────────────────
 *   The frontend should refresh its conversation list — the conversation was
 *   removed or never existed for this account.
 */
public class ChatNotFoundException extends RuntimeException {
    public ChatNotFoundException(String message) {
        super(message);
    }
}