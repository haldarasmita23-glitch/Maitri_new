package com.maitri.exception;

/**
 * ChatAccessDeniedException — thrown when an authenticated account attempts
 * to access a chat conversation they are not a participant of (Phase 11).
 *
 * ─── WHEN THIS IS THROWN ─────────────────────────────────────────────────────
 *   - GET /api/chats/{chatId} for a conversation between other parties
 *   - POST /api/chats/{chatId}/messages for a conversation the user isn't in
 *   - PUT /api/chats/{chatId}/read for a conversation the user isn't in
 *   - USER attempting to message another USER
 *   - VENDOR attempting to message another VENDOR
 *
 * ─── HTTP STATUS ─────────────────────────────────────────────────────────────
 *   Returns HTTP 403 Forbidden (handled by GlobalExceptionHandler)
 *
 * ─── SECURITY CONSIDERATION ──────────────────────────────────────────────────
 *   Prevents users from accessing other parties' conversations.
 *   Also enforces USER ↔ VENDOR restriction (no USER↔USER or VENDOR↔VENDOR).
 */
public class ChatAccessDeniedException extends RuntimeException {
    public ChatAccessDeniedException(String message) {
        super(message);
    }
}