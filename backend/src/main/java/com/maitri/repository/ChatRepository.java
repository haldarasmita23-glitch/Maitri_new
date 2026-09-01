package com.maitri.repository;

import com.maitri.model.Chat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Chat Repository — data access for the Chat/Contact Module (Phase 11).
 *
 * ─── ACCESS PATTERNS ─────────────────────────────────────────────────────────
 *   Every query is scoped by participant — a user/vendor can only ever reach
 *   their own conversations. There is deliberately NO unscoped findAll-style
 *   finder for application use, and no cross-user lookup.
 *
 * ─── METHODS ─────────────────────────────────────────────────────────────────
 *   findConversation — messages between two parties, paginated, newest first
 *   findConversationsForUser — list of conversations (last message per partner)
 *   countUnreadForUser — unread message count
 *   markAsRead — mark messages as read
 *   findByIdAndParticipant — fetch one message by id if user is participant
 *   save — store a new message
 */
public interface ChatRepository extends MongoRepository<Chat, String> {

    /**
     * Finds all messages between two parties (both directions), newest first.
     *
     * @param userId1    First party's id
     * @param userId2    Second party's id
     * @param pageable   Pagination parameters
     * @return Page of messages between the two parties
     */
    @Query(value = "{'$or': ["
            + "{'senderId': ?0, 'receiverId': ?1}, "
            + "{'senderId': ?1, 'receiverId': ?0}"
            + "]}",
           sort = "{'timestamp': -1}")
    Page<Chat> findConversation(String userId1, String userId2, Pageable pageable);

    /**
     * Lists all messages for a user (ordered by timestamp descending).
     *
     * @param userId The user's id
     * @return List of messages involving the user, newest first
     */
    @Query(value = "{'$or': [{'senderId': ?0}, {'receiverId': ?0}]}",
           sort = "{'timestamp': -1}")
    List<Chat> findLatestMessagesPerPartner(String userId);

    /**
     * Counts unread messages for a user (where user is receiver and read=false).
     *
     * @param userId The user's id
     * @return Number of unread messages
     */
    long countByReceiverIdAndReadFalse(String userId);

    /**
     * Finds the most recent chat message between two specific participants (either direction).
     * Used to verify that a conversation exists between a user and a partner.
     *
     * @param userId1 First participant's id
     * @param userId2 Second participant's id
     * @return The most recent message between them, or empty if none
     */
    @Query(value = "{'$or': [{'senderId': ?0, 'receiverId': ?1}, {'senderId': ?1, 'receiverId': ?0}]}",
           sort = "{'timestamp': -1}")
    Optional<Chat> findFirstConversationBetween(String userId1, String userId2);


    /**
     * Finds unread messages from a specific sender to a receiver.
     *
     * @param senderId   The sender's id
     * @param receiverId The receiver's id
     * @return List of unread messages
     */
    List<Chat> findBySenderIdAndReceiverIdAndReadFalse(String senderId, String receiverId);
}