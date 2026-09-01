package com.maitri.model;

/**
 * ConversationStatus — Status of a customer-to-vendor conversation request.
 *
 * PENDING  — Customer sent the initial conversation request, waiting for vendor acceptance.
 * ACCEPTED — Vendor accepted the conversation request; live messaging is active.
 * REJECTED — Vendor rejected the conversation request.
 */
public enum ConversationStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
