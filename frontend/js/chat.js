/**
 * Maitri — Chat / Contact Module (Phase 11)
 *
 * Handles conversation listing, message sending, and unread counting.
 * All API calls go through the centralized api.js client.
 */

import { API } from './api.js';

/**
 * Initialise the chat module.
 * - Loads conversations on page mount
 * - Sets up event listeners for send message, mark read
 * - Updates unread badge on bell/icon
 */
export function initChat() {
  // Load conversations on module init
  loadConversations();

  // Attach any global event listeners here
  // e.g., form submit for new message, bell click, etc.
}

/**
 * Load the authenticated user's conversations,
 * populating the conversation list UI.
 */
async function loadConversations() {
  try {
    const result = await API.getChats();
    if (!result.ok) {
      logError('Failed to load conversations', result);
      return;
    }

    const conversations = result.data || [];
    displayConversations(conversations);
    updateUnreadBadge(result.data);
  } catch (err) {
    logError('Error loading conversations', err);
  }
}

/**
 * Display conversations in the UI.
 * Each conversation shows the partner's name/ID, last message preview,
 * and an unread badge if applicable.
 *
 * @param {Array} conversations - List of conversation summaries from the API
 */
function displayConversations(conversations) {
  const chatListElement = document.querySelector('#chat-conversation-list');
  if (!chatListElement) {
    logWarning('Chat conversation list element not found');
    return;
  }

  // Clear existing list
  chatListElement.innerHTML = '';

  if (conversations.length === 0) {
    chatListElement.innerHTML = '<li class="empty-state">No conversations yet. Start a chat with a vendor.</li>';
    return;
  }

  conversations.forEach(conv => {
    const listItem = document.createElement('li');
    listItem.className = 'conversation-item';
    listItem.innerHTML = `
      <div class="conversation-preview">
        <span class="partner-role ${conv.otherPartyRole.toLowerCase()}">${conv.otherPartyRole}</span>
        <span class="partner-name">${conv.otherPartyId || 'Unknown'}</span>
      </div>
      <div class="conversation-last-message">
        ${conv.lastMessage ? truncateText(conv.lastMessage, 50) : 'No messages yet'}
      </div>
    `;

    // Click handler to open conversation detail
    listItem.addEventListener('click', () => {
      openConversationDetail(conv.chatId, conv.otherPartyId, conv.otherPartyRole);
    });

    chatListElement.appendChild(listItem);
  });
}

/**
 * Truncate text to a maximum length, adding ellipsis if truncated.
 *
 * @param {string} text - The text to truncate
 * @param {number} maxLength - Maximum characters before truncation
 * @returns {string} Truncated text with ellipsis if needed
 */
function truncateText(text, maxLength) {
  if (text.length <= maxLength) {
    return text;
  }
  return text.substring(0, maxLength) + '...';
}

/**
 * Load and display the conversation detail page for a specific partner.
 *
 * @param {string} chatId - The conversation/chat ID
 * @param {string} otherPartyId - The partner's account ID
 * @param {string} otherPartyRole - The partner's role (USER or VENDOR)
 */
async function openConversationDetail(chatId, otherPartyId, otherPartyRole) {
  try {
    const convResult = await API.getChat(chatId);
    if (!convResult.ok) {
      logError('Failed to load conversation', convResult);
      return;
    }

    const messages = convResult.data || [];

    // Render the conversation detail view
    renderConversationDetail(messages, otherPartyId, otherPartyRole);

    // Update unread count after viewing
    await API.markChatRead(chatId);
    await API.getUnreadCount();
  } catch (err) {
    logError('Error opening conversation detail', err);
  }
}

/**
 * Render the conversation detail view with message list and send form.
 *
 * @param {Array} messages - Array of message response objects
 * @param {string} otherPartyId - The partner's account ID
 * @param {string} otherPartyRole - The partner's role
 */
function renderConversationDetail(messages, otherPartyId, otherPartyRole) {
  const chatDetailContainer = document.querySelector('#chat-detail-container');
  if (!chatDetailContainer) {
    logWarning('Chat detail container element not found');
    return;
  }

  // Clear existing content
  chatDetailContainer.innerHTML = '';

  // Render message history
  const messageList = document.createElement('div');
  messageList.className = 'message-list';
  messageList.innerHTML = renderMessages(messages);

  chatDetailContainer.appendChild(messageList);

  // Render send message form
  const sendForm = document.createElement('form');
  sendForm.className = 'send-message-form';
  sendForm.innerHTML = `
    <div class="form-group">
      <textarea id="message-input" placeholder="Type a message..." rows="2" maxlength="1000"></textarea>
    </div>
    <button type="submit" class="btn-send">Send</button>
  `;

  chatDetailContainer.appendChild(sendForm);

  // Handle form submission
  sendForm.addEventListener('submit', async (e) => {
    e.preventDefault();

    const textarea = sendForm.querySelector('#message-input');
    const message = textarea.value.trim();
    if (!message) {
      return;
    }

    // Determine message type - default to TEXT, could be extended to support IMAGE
    const messageType = 'TEXT';

    try {
      await API.sendMessage(chatId, { message, messageType });
      textarea.value = '';

      // Refresh the conversation
      const newResult = await API.getChat(chatId);
      if (newResult.ok) {
        renderConversationDetail(newResult.data, otherPartyId, otherPartyRole);
      }

      // Update unread count
      await API.getUnreadCount();
    } catch (err) {
      logError('Error sending message', err);
    }
  });
}

/**
 * Render the message list HTML.
 *
 * @param {Array} messages - Array of message response objects
 * @returns {string} HTML string for the message list
 */
function renderMessages(messages) {
  if (!messages || messages.length === 0) {
    return '<p class="no-messages">No messages yet.</p>';
  }

  return messages.map(msg => `
    <div class="message ${msg.isOwnMessage ? 'own' : 'partner'}">
      <div class="message-content">
        ${msg.message}
      </div>
      <div class="message-meta">
        ${msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
      </div>
    </div>
  `).join('');
}

/**
 * Update the unread message badge in the UI.
 *
 * @param {Array} conversations - Optional conversation list to count from
 */
export function updateUnreadBadge(conversations) {
  let unreadCount = 0;

  if (conversations && Array.isArray(conversations)) {
    conversations.forEach(conv => {
      if (conv && conv.unreadCount !== undefined) {
        unreadCount += conv.unreadCount;
      }
    });
  } else {
    // Try to get fresh count from API
    // This is a best-effort update; the navbar badge is primarily from the API endpoint
  }

  const badgeElement = document.querySelector('.unread-badge');
  if (badgeElement) {
    if (unreadCount > 0) {
      badgeElement.textContent = unreadCount;
      badgeElement.style.display = 'block';
    } else {
      badgeElement.style.display = 'none';
    }
  }
}

/**
 * Log an error to the console (can be replaced with proper logging).
 *
 * @param {string} message - Error message
 * @param {Error} err - Error object
 */
function logError(message, err) {
  console.error(`[Chat] ${message}:`, err);
}

/**
 * Log a warning to the console.
 *
 * @param {string} message - Warning message
 */
function logWarning(message) {
  console.warn(`[Chat] ${message}`);
}