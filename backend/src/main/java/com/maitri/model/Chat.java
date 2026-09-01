package com.maitri.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Chat — MongoDB Document representing a single chat message (Phase 11).
 *
 * ─── BUSINESS RULES ──────────────────────────────────────────────────────────
 *   - Each document represents a single message in a conversation
 *   - Conversations are reconstructed by querying messages between two parties
 *   - USER ↔ VENDOR messaging is the primary supported flow
 *   - ADMIN can participate in any conversation
 *   - USER ↔ USER and VENDOR ↔ VENDOR are NOT supported
 *   - Messages are immutable once sent (no edit/delete in V1)
 *   - Read status is tracked per message
 *
 * ─── COLLECTION ──────────────────────────────────────────────────────────────
 *   MongoDB collection name: "chats"
 *
 * ─── FIELDS (per DATABASE_DESIGN.md §6) ──────────────────────────────────────
 *   _id           — MongoDB ObjectId (auto-generated, String representation)
 *   senderId      — Reference to users._id or vendors._id (the sender)
 *   senderRole    — USER | VENDOR | ADMIN
 *   receiverId    — Reference to users._id or vendors._id (the receiver)
 *   receiverRole  — USER | VENDOR | ADMIN
 *   message       — Message content (text or image URL)
 *   messageType   — TEXT | IMAGE
 *   timestamp     — When the message was sent
 *   read          — false = unread
 *
 * ─── INDEXES ─────────────────────────────────────────────────────────────────
 *   { senderId, receiverId } — Efficient conversation reconstruction
 *   { receiverId, read }     — Unread count queries
 *
 * @Document("chats") — Maps this class to the MongoDB "chats" collection.
 * @CompoundIndex — Compound index for conversation reconstruction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chats")
@CompoundIndex(name = "sender_receiver_idx", def = "{'senderId': 1, 'receiverId': 1}")
public class Chat {

    /**
     * MongoDB document ID.
     * Spring Data automatically maps this to MongoDB's _id field.
     */
    @Id
    private String id;

    /**
     * Reference to the sender account (users._id or vendors._id).
     * All reads/writes are scoped to authenticated identity — never trust client-supplied senderId.
     */
    @Indexed
    private String senderId;

    /**
     * Role of the sender at creation time (USER | VENDOR | ADMIN).
     */
    private Role senderRole;

    /**
     * Reference to the receiver account (users._id or vendors._id).
     */
    @Indexed
    private String receiverId;

    /**
     * Role of the receiver at creation time (USER | VENDOR | ADMIN).
     */
    private Role receiverRole;

    /**
     * Message content (plain text or image URL).
     * Kept for backward compatibility with legacy messages and clients.
     */
    private String message;

    /**
     * Original raw text message as typed by the sender.
     * Preserved immutably once sent.
     */
    private String originalMessage;

    /**
     * Translated message content in the receiver's preferred language.
     */
    private String translatedMessage;

    /**
     * Detected or configured source language code ("en", "hi", "kn").
     */
    private String sourceLanguage;

    /**
     * Target language code ("en", "hi", "kn") corresponding to receiver's preferredLanguage.
     */
    private String targetLanguage;

    /**
     * Status of the message translation.
     */
    private TranslationStatus translationStatus;

    /**
     * Type of message content.
     */
    private MessageType messageType;

    /**
     * Timestamp of when this message was sent.
     * Set once at creation. Never changed after that.
     */
    private LocalDateTime timestamp;

    /**
     * Read flag. false = unread.
     */
    @Builder.Default
    private boolean read = false;

    /**
     * Conversation status: PENDING, ACCEPTED, REJECTED.
     */
    @Builder.Default
    private ConversationStatus status = ConversationStatus.PENDING;
}