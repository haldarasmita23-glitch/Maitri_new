package com.maitri.model;

/**
 * MessageType — Type of a chat message (Phase 11).
 *
 * ─── VALUES ──────────────────────────────────────────────────────────────────
 *   TEXT  — Plain text message.
 *   IMAGE — Image message (URL reference).
 *
 * Stored in MongoDB as a plain string (enum name).
 */
public enum MessageType {

    /** Plain text message. */
    TEXT,

    /** Image message (stored as URL). */
    IMAGE
}