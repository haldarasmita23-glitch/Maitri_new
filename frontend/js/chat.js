/**
 * Maitri — Chat / Contact Module (Phase 11 + Live Translation)
 *
 * Handles conversation listing, full message thread rendering, message sending,
 * live translation badge display, and unread counting.
 * All API calls go through the centralized api.js client.
 *
 * Pages served:
 *   - chat.html        -> conversation list   (initChatList)
 *   - chat-detail.html -> full thread view    (initChatDetail)
 *
 * Translation Display Rules:
 *   - Sender sees their ORIGINAL message (own bubble, no badge)
 *   - Receiver sees the TRANSLATED message (partner bubble) with:
 *       Globe Translated from {lang} | View original
 *   - If translationStatus === FAILED or UNAVAILABLE, receiver sees
 *     original text with a "Translation unavailable" indicator
 *   - If translationStatus === NOT_REQUIRED (same language), no badge shown
 *
 * Real-time strategy: REST polling every POLL_INTERVAL_MS milliseconds.
 * Only re-renders if the message count has changed (avoids flicker).
 */

// -- Constants -----------------------------------------------------------
const POLL_INTERVAL_MS  = 5000;   // 5-second polling interval
const MESSAGES_PER_PAGE = 30;     // messages fetched per request

// -- Module-level state --------------------------------------------------
let _pollTimer          = null;   // setInterval handle for polling
let _lastMsgCount       = -1;     // last known message count (for poll diff)
let _currentChatId      = null;   // currently open partner ID
let _currentPartnerName = '';
let _currentPartnerRole = '';

// -- Initialisation ------------------------------------------------------

document.addEventListener('DOMContentLoaded', () => {
  // Initialize Navbar
  if (typeof Navbar !== 'undefined') Navbar.init();

  // Conversation list page (chat.html)
  if (document.getElementById('chat-conversation-list')) {
    initChatList();
    initStartNewChat();
  }

  // Conversation detail page (chat-detail.html)
  if (document.getElementById('chat-detail-container')) {
    initChatDetail();
  }

  // Language change listener -- re-render with new language strings
  window.addEventListener('maitri:language-change', () => {
    if (document.getElementById('chat-conversation-list')) {
      initChatList();
    }
    if (document.getElementById('chat-detail-container') && _currentChatId) {
      _lastMsgCount = -1; // force re-render
      loadAndRenderThread(_currentChatId, _currentPartnerName, _currentPartnerRole);
    }
  });

  // Stop polling when user navigates away
  window.addEventListener('pagehide', stopPolling);
  window.addEventListener('beforeunload', stopPolling);
});

// -- Helpers: URL resolution ---------------------------------------------

function getActiveRole() {
  if (typeof AuthSession !== 'undefined' && typeof AuthSession.currentUser === 'function') {
    const u = AuthSession.currentUser();
    if (u && u.role) return u.role.toUpperCase().replace(/^ROLE_/, '');
  }
  try {
    const raw = localStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA);
    if (!raw) return null;
    const u = JSON.parse(raw);
    return (u.role || '').toUpperCase().replace(/^ROLE_/, '');
  } catch {
    return null;
  }
}

function isVendorUser() {
  return getActiveRole() === 'VENDOR';
}

function isAdminUser() {
  return getActiveRole() === 'ADMIN';
}

function isCustomerUser() {
  const r = getActiveRole();
  return r === 'USER' || r === 'CUSTOMER' || (!isVendorUser() && !isAdminUser());
}

function getLoginUrl() {
  return window.location.pathname.includes('/pages/') ? 'login.html' : 'pages/login.html';
}

function getVendorsUrl() {
  return window.location.pathname.includes('/pages/') ? 'vendors.html' : 'pages/vendors.html';
}

function getChatUrl() {
  return window.location.pathname.includes('/pages/') ? 'chat.html' : 'pages/chat.html';
}

// -- Helpers: polling lifecycle ------------------------------------------

function startPolling(chatId, partnerName, partnerRole) {
  stopPolling();
  _pollTimer = setInterval(async () => {
    await loadAndRenderThread(chatId, partnerName, partnerRole, true);
  }, POLL_INTERVAL_MS);
}

function stopPolling() {
  if (_pollTimer !== null) {
    clearInterval(_pollTimer);
    _pollTimer = null;
  }
}

// -- Start New Chat button -----------------------------------------------

function initStartNewChat() {
  const btn = document.getElementById('start-new-chat');
  const actions = document.querySelector('.chat-actions, #chat-actions-container');
  if (!btn && !actions) return;

  if (isVendorUser() || isAdminUser()) {
    // Vendors and Admins do not initiate new conversations or browse vendors from chat
    if (btn) {
      btn.style.display = 'none';
      btn.remove();
    }
    if (actions) {
      actions.style.display = 'none';
      actions.remove();
    }
    return;
  }

  if (btn) {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      if (isVendorUser() || isAdminUser()) return;
      window.location.href = getVendorsUrl();
    });
  }
}


// =======================================================================
// CONVERSATION LIST  (chat.html)
// =======================================================================

async function initChatList() {
  const listEl = document.getElementById('chat-conversation-list');
  if (!listEl) return;

  const isVendor = isVendorUser();
  const isAdmin = isAdminUser();

  if (isVendor || isAdmin) {
    const heading = document.getElementById('chat-heading');
    const subtitle = document.querySelector('.section-subtitle');
    if (isVendor) {
      if (heading) heading.textContent = 'Customer Inquiries & Messages';
      if (subtitle) subtitle.textContent = 'Incoming requests and conversations from local residents and customers';
    } else if (isAdmin) {
      if (heading) heading.textContent = 'Administrative Messages';
      if (subtitle) subtitle.textContent = 'Platform communications';
    }
    const actions = document.querySelector('.chat-actions, #chat-actions-container');
    if (actions) {
      actions.style.display = 'none';
      actions.remove();
    }
    const btn = document.getElementById('start-new-chat');
    if (btn) {
      btn.style.display = 'none';
      btn.remove();
    }
  }

  const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  if (!token) {
    const loginText = typeof I18n !== 'undefined'
      ? I18n.t('chat.loginRequired', { loginUrl: getLoginUrl() })
      : 'Please <a href="' + getLoginUrl() + '">log in</a> to view your messages.';
    listEl.innerHTML = '<li class="empty-state"><p>' + loginText + '</p></li>';
    return;
  }

  try {
    const result = await window.API.getChats();
    if (!result || !result.success) {
      listEl.innerHTML = '<li class="empty-state">' + escapeHtml(result && result.message ? result.message : 'Failed to load conversations.') + '</li>';
      return;
    }

    const conversations = result.data || [];
    displayConversations(conversations);
    updateUnreadBadge(conversations);
  } catch (err) {
    console.error('[Chat] Error loading conversations:', err);
    const errMsg = typeof I18n !== 'undefined'
      ? I18n.t('messages.connectionError')
      : 'Unable to load conversations. Please try again later.';
    listEl.innerHTML = '<li class="empty-state">' + escapeHtml(errMsg) + '</li>';
  }
}

function displayConversations(conversations) {
  const chatListElement = document.querySelector('#chat-conversation-list');
  if (!chatListElement) return;

  chatListElement.innerHTML = '';

  const isVendor = isVendorUser();
  const isAdmin = isAdminUser();

  if (!conversations || conversations.length === 0) {
    const noConvs = isVendor
      ? (typeof I18n !== 'undefined' ? I18n.t('chat.noCustomerMessages') : 'No customer messages yet. Customer requests will appear here.')
      : (isAdmin
          ? 'No administrative messages yet.'
          : (typeof I18n !== 'undefined' ? I18n.t('chat.noConversations') : 'No conversations yet. Start a chat with a vendor.'));
    
    const emptyAction = (!isVendor && !isAdmin)
      ? '<br><a href="' + getVendorsUrl() + '" class="btn btn--primary btn--sm mt-3" data-i18n="chat.browseVendors">Browse Vendors</a>'
      : '';
    chatListElement.innerHTML = '<li class="empty-state"><p>' + escapeHtml(noConvs) + '</p>' + emptyAction + '</li>';
    return;
  }

  conversations.forEach(function(conv) {
    var listItem = document.createElement('li');
    listItem.className = 'conversation-item';
    listItem.setAttribute('role', 'button');
    listItem.setAttribute('tabindex', '0');

    var unreadBadge = conv.unreadCount > 0
      ? '<span class="conversation-unread">' + conv.unreadCount + '</span>'
      : '';

    var partnerName = conv.otherPartyName || conv.otherPartyId
      || (typeof I18n !== 'undefined' ? I18n.t('chat.unknownPartner') : 'Unknown');
    var noMsgText = typeof I18n !== 'undefined' ? I18n.t('chat.noMessages') : 'No messages yet';

    // Status indicator
    var status = conv.status || 'ACCEPTED';
    var statusBadge = '';
    var acceptBtnHtml = '';

    if (isVendor) {
      if (status === 'PENDING') {
        var pendingLbl = typeof I18n !== 'undefined' ? I18n.t('chat.pending') : 'Pending';
        var acceptLbl = typeof I18n !== 'undefined' ? I18n.t('chat.accept') : 'Accept';
        statusBadge = '<span class="badge" style="background:#fef3c7; color:#92400e; font-size:0.7rem; font-weight:600; padding:2px 8px; border-radius:12px; margin-left:8px;">⏳ ' + escapeHtml(pendingLbl) + '</span>';
        acceptBtnHtml = '<button type="button" class="btn btn--primary btn--xs js-list-accept-btn" data-chat-id="' + escapeAttr(conv.otherPartyId) + '" style="margin-left:auto; z-index:2;">' + escapeHtml(acceptLbl) + '</button>';
      } else {
        var acceptedLbl = typeof I18n !== 'undefined' ? I18n.t('chat.accepted') : 'Accepted';
        statusBadge = '<span class="badge" style="background:#d1fae5; color:#065f46; font-size:0.7rem; font-weight:600; padding:2px 8px; border-radius:12px; margin-left:8px;">✓ ' + escapeHtml(acceptedLbl) + '</span>';
      }
    } else {
      if (status === 'PENDING') {
        var waitingLbl = typeof I18n !== 'undefined' ? I18n.t('chat.waitingForVendor') : 'Waiting for vendor acceptance';
        statusBadge = '<span class="badge" style="background:#fef3c7; color:#92400e; font-size:0.7rem; font-weight:600; padding:2px 8px; border-radius:12px; margin-left:8px;">⏳ ' + escapeHtml(waitingLbl) + '</span>';
      }
    }

    // Translation indicator for conversation list preview
    var isTranslatedPreview = !conv.lastMessageIsOwn && conv.translationStatus === 'TRANSLATED';
    var transIndicator = isTranslatedPreview ? ' <span aria-label="Translated">\uD83C\uDF10</span>' : '';

    listItem.innerHTML =
      '<div class="conversation-preview" style="display:flex; align-items:center; width:100%;">' +
        '<span class="partner-role ' + escapeHtml((conv.otherPartyRole || '').toLowerCase()) + '">' + escapeHtml(conv.otherPartyRole || '') + '</span>' +
        '<span class="partner-name" style="margin-left:6px;">' + escapeHtml(partnerName) + '</span>' +
        statusBadge +
        unreadBadge +
        acceptBtnHtml +
      '</div>' +
      '<div class="conversation-last-message">' +
        (conv.lastMessage ? (escapeHtml(truncateText(conv.lastMessage, 50)) + transIndicator) : escapeHtml(noMsgText)) +
      '</div>';

    var open = (function(id, name, role) {
      return function() { openConversationDetail(id, name, role); };
    })(conv.otherPartyId, partnerName, conv.otherPartyRole);

    listItem.addEventListener('click', function(e) {
      if (e.target.closest('.js-list-accept-btn')) return;
      open();
    });

    listItem.addEventListener('keydown', function(e) {
      if (e.key === 'Enter' || e.key === ' ') {
        if (e.target.closest('.js-list-accept-btn')) return;
        e.preventDefault();
        open();
      }
    });

    // Accept button listener
    var acceptBtn = listItem.querySelector('.js-list-accept-btn');
    if (acceptBtn) {
      acceptBtn.addEventListener('click', async function(e) {
        e.stopPropagation();
        acceptBtn.disabled = true;
        acceptBtn.textContent = typeof I18n !== 'undefined' ? I18n.t('chat.accepting') : 'Accepting...';
        try {
          var res = await window.API.acceptConversation(conv.otherPartyId);
          if (res && res.success) {
            if (typeof Toast !== 'undefined') Toast.success(
              'Accepted',
              typeof I18n !== 'undefined' ? I18n.t('chat.acceptedSuccess') : 'Conversation accepted!'
            );
            await initChatList();
          } else {
            if (typeof Toast !== 'undefined') Toast.error('Error', res && res.message ? res.message : 'Could not accept');
            acceptBtn.disabled = false;
            acceptBtn.textContent = typeof I18n !== 'undefined' ? I18n.t('chat.accept') : 'Accept';
          }
        } catch (err) {
          console.error('[Chat] Accept error:', err);
          acceptBtn.disabled = false;
          acceptBtn.textContent = typeof I18n !== 'undefined' ? I18n.t('chat.accept') : 'Accept';
        }
      });
    }

    chatListElement.appendChild(listItem);
  });
}

function truncateText(text, maxLength) {
  if (!text) return '';
  return text.length <= maxLength ? text : text.substring(0, maxLength) + '...';
}

function openConversationDetail(chatId, otherPartyName, otherPartyRole) {
  var params = new URLSearchParams({ chatId: chatId });
  if (otherPartyName) params.set('name', otherPartyName);
  if (otherPartyRole) params.set('role', otherPartyRole);
  window.location.href = 'chat-detail.html?' + params.toString();
}


// =======================================================================
// CONVERSATION DETAIL  (chat-detail.html)
// =======================================================================

async function initChatDetail() {
  var container = document.getElementById('chat-detail-container');
  if (!container) return;

  var token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  if (!token) {
    var loginText2 = typeof I18n !== 'undefined'
      ? I18n.t('chat.loginRequired', { loginUrl: getLoginUrl() })
      : 'Please <a href="' + getLoginUrl() + '">log in</a> to view this conversation.';
    container.innerHTML = '<div class="empty-state"><p>' + loginText2 + '</p></div>';
    return;
  }

  var params2 = new URLSearchParams(window.location.search);
  var chatId      = params2.get('chatId');
  var partnerName = params2.get('name') || '';
  var partnerRole = params2.get('role') || '';

  if (!chatId) {
    var noChat = typeof I18n !== 'undefined' ? I18n.t('chat.noConversationSelected') : 'No conversation selected.';
    var backLbl = typeof I18n !== 'undefined' ? I18n.t('chat.backToMessages') : 'Back to messages';
    container.innerHTML =
      '<div class="empty-state">' +
        '<p>' + escapeHtml(noChat) +
        ' <a href="' + getChatUrl() + '" class="btn btn--primary btn--sm" style="margin-left:0.5rem;">' + escapeHtml(backLbl) + '</a></p>' +
      '</div>';
    return;
  }

  // Store state for polling + language-change handler
  _currentChatId      = chatId;
  _currentPartnerName = partnerName;
  _currentPartnerRole = partnerRole;

  // Build the chrome (window header + message stream + send form) once
  buildChatWindowChrome(container, chatId, partnerName, partnerRole);

  // Initial load
  await loadAndRenderThread(chatId, partnerName, partnerRole, false);

  // Start polling
  startPolling(chatId, partnerName, partnerRole);
}

/**
 * Builds the static scaffold of the chat window.
 * Called once on page load; subsequent renders only update #chat-message-stream.
 */
function buildChatWindowChrome(container, chatId, partnerName, partnerRole) {
  var placeholderText = typeof I18n !== 'undefined' ? I18n.t('chat.typeMessage') : 'Type a message...';
  var sendLabel       = typeof I18n !== 'undefined' ? I18n.t('chat.send') : 'Send';
  var backLabel       = typeof I18n !== 'undefined' ? I18n.t('chat.backToMessages') : 'Back';
  var roleEmoji       = (partnerRole || '').toUpperCase() === 'VENDOR' ? '\uD83C\uDFEA' : '\uD83D\uDC64';

  container.innerHTML =
    '<!-- Chat Window Header -->' +
    '<div class="chat-window-header">' +
      '<div class="chat-window-partner">' +
        '<div class="chat-window-avatar" aria-hidden="true">' + roleEmoji + '</div>' +
        '<div><div class="chat-window-name" id="chat-partner-name">' + escapeHtml(partnerName || 'Partner') + '</div></div>' +
      '</div>' +
      '<a href="' + getChatUrl() + '" class="btn btn--ghost btn--sm" aria-label="' + escapeAttr(backLabel) + '">\u2190 ' + escapeHtml(backLabel) + '</a>' +
    '</div>' +
    '<!-- Language Banner -->' +
    '<div class="chat-lang-banner" id="chat-lang-banner" style="display:none;" aria-live="polite"></div>' +
    '<!-- Message Stream -->' +
    '<div class="message-preview" id="chat-message-stream" role="log" aria-live="polite" aria-label="Messages">' +
      '<div class="spinner" aria-label="Loading messages"></div>' +
    '</div>' +
    '<!-- Send Form -->' +
    '<form class="send-message-form" id="chat-send-form" autocomplete="off">' +
      '<div class="form-group">' +
        '<textarea id="message-input" placeholder="' + escapeAttr(placeholderText) + '" rows="2" maxlength="1000" aria-label="Message text" required></textarea>' +
      '</div>' +
      '<button type="submit" id="chat-send-btn" class="btn btn--primary">' + escapeHtml(sendLabel) + '</button>' +
    '</form>';

  // "View original" toggle -- event delegation on the message stream
  var stream = container.querySelector('#chat-message-stream');
  stream.addEventListener('click', function(e) {
    var toggleBtn = e.target.closest('.btn-toggle-original');
    if (!toggleBtn) return;
    var msgEl = toggleBtn.closest('.message');
    if (!msgEl) return;
    var contentEl = msgEl.querySelector('.message-content');
    if (!contentEl) return;

    var isShowingOriginal = toggleBtn.getAttribute('data-showing-original') === 'true';
    var orig  = msgEl.getAttribute('data-original');
    var trans = msgEl.getAttribute('data-translated');

    if (isShowingOriginal) {
      contentEl.textContent = trans;
      toggleBtn.setAttribute('data-showing-original', 'false');
      toggleBtn.textContent = typeof I18n !== 'undefined' ? I18n.t('chat.viewOriginal') : 'View original';
    } else {
      contentEl.textContent = orig;
      toggleBtn.setAttribute('data-showing-original', 'true');
      toggleBtn.textContent = typeof I18n !== 'undefined' ? I18n.t('chat.viewTranslation') : 'View translation';
    }
  });

  // Send form submission
  var sendForm = container.querySelector('#chat-send-form');
  sendForm.addEventListener('submit', async function(e) {
    e.preventDefault();
    var textarea = sendForm.querySelector('#message-input');
    var sendBtn  = sendForm.querySelector('#chat-send-btn');
    var message  = textarea.value.trim();

    if (!message) {
      var emptyMsg = typeof I18n !== 'undefined' ? I18n.t('validation.messageRequired') : 'Please type a message before sending.';
      if (typeof Toast !== 'undefined') Toast.warning('Empty message', emptyMsg);
      return;
    }

    sendBtn.disabled    = true;
    sendBtn.textContent = typeof I18n !== 'undefined' ? I18n.t('chat.sending') : 'Sending...';

    try {
      var result = await window.API.sendMessage(chatId, { message: message, messageType: 'TEXT' });
      if (!result || !result.success) {
        var errMsg = result && result.message ? result.message : 'Please try again.';
        if (typeof Toast !== 'undefined') Toast.error(
          typeof I18n !== 'undefined' ? I18n.t('messages.unknownError') : 'Send failed', errMsg
        );
        return;
      }

      textarea.value = '';
      _lastMsgCount  = -1; // force full re-render on next poll/load
      await loadAndRenderThread(chatId, partnerName, partnerRole, false);
      await refreshUnreadBadge();
    } catch (err) {
      console.error('[Chat] Error sending message:', err);
      if (typeof Toast !== 'undefined') Toast.error(
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Network error',
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Please check your connection and try again.'
      );
    } finally {
      sendBtn.disabled    = false;
      sendBtn.textContent = typeof I18n !== 'undefined' ? I18n.t('chat.send') : 'Send';
    }
  });
}

/**
 * Fetches all messages for the conversation and renders them in the stream.
 * In silent mode (polling), only updates the DOM if message count changed.
 */
async function loadAndRenderThread(chatId, partnerName, partnerRole, silent) {
  var stream = document.getElementById('chat-message-stream');
  if (!stream) return;

  if (!silent) {
    stream.innerHTML = '<div class="spinner" aria-label="Loading messages"></div>';
  }

  try {
    var result = await window.API.getChatMessages(chatId, 0, MESSAGES_PER_PAGE);

    if (!result || !result.success) {
      if (!silent) {
        var backBtn2 = typeof I18n !== 'undefined' ? I18n.t('chat.backToMessages') : 'Back to messages';
        stream.innerHTML =
          '<div class="empty-state">' +
            '<p>' + escapeHtml(result && result.message ? result.message : 'Unable to load messages.') + '</p>' +
            '<a href="' + getChatUrl() + '" class="btn btn--primary btn--sm" style="margin-top:0.75rem;">' + escapeHtml(backBtn2) + '</a>' +
          '</div>';
      }
      return;
    }

    // Spring Page -- messages arrive newest-first; reverse for top-to-bottom display
    var pageData  = result.data || {};
    var msgs      = (pageData.content || []).slice().reverse(); // oldest first
    var totalMsgs = pageData.totalElements !== undefined ? pageData.totalElements : msgs.length;

    // Check conversation status
    var convStatus = 'ACCEPTED';
    if (msgs.length > 0 && msgs[msgs.length - 1].status) {
      convStatus = msgs[msgs.length - 1].status;
    }

    var isVendor = isVendorUser();
    var isUser = isCustomerUser();

    var statusBanner = document.getElementById('chat-status-banner');
    var sendForm = document.getElementById('chat-send-form');
    var textarea = sendForm ? sendForm.querySelector('#message-input') : null;
    var sendBtn  = sendForm ? sendForm.querySelector('#chat-send-btn') : null;

    if (convStatus === 'PENDING') {
      if (isVendor) {
        if (!statusBanner) {
          statusBanner = document.createElement('div');
          statusBanner.id = 'chat-status-banner';
          statusBanner.className = 'card';
          statusBanner.style.cssText = 'background: #fffbeb; border: 1px solid #f59e0b; padding: var(--space-3); border-radius: var(--radius-md); margin-bottom: var(--space-3); display: flex; align-items: center; justify-content: space-between; gap: var(--space-3);';
          stream.parentNode.insertBefore(statusBanner, stream);
        }
        var bannerTitle = typeof I18n !== 'undefined' ? I18n.t('chat.requestPendingBanner') : 'Customer has sent a conversation request.';
        var bannerSub = typeof I18n !== 'undefined' ? I18n.t('chat.acceptToReply') : 'Accept the conversation request above to reply.';
        var bannerBtn = typeof I18n !== 'undefined' ? I18n.t('chat.accept') : 'Accept';
        statusBanner.innerHTML =
          '<div>' +
            '<div style="font-weight: 600; font-size: var(--font-size-sm); color: #92400e;">📩 ' + escapeHtml(bannerTitle) + '</div>' +
            '<div style="font-size: var(--font-size-xs); color: #b45309;">' + escapeHtml(bannerSub) + '</div>' +
          '</div>' +
          '<button type="button" id="btn-detail-accept" class="btn btn--primary btn--sm" style="flex-shrink:0;">' + escapeHtml(bannerBtn) + '</button>';

        var detailAcceptBtn = statusBanner.querySelector('#btn-detail-accept');
        if (detailAcceptBtn) {
          detailAcceptBtn.onclick = async function() {
            detailAcceptBtn.disabled = true;
            detailAcceptBtn.textContent = typeof I18n !== 'undefined' ? I18n.t('chat.accepting') : 'Accepting...';
            try {
              var res = await window.API.acceptConversation(chatId);
              if (res && res.success) {
                if (typeof Toast !== 'undefined') Toast.success(
                  'Accepted',
                  typeof I18n !== 'undefined' ? I18n.t('chat.acceptedSuccess') : 'Conversation accepted!'
                );
                _lastMsgCount = -1;
                await loadAndRenderThread(chatId, partnerName, partnerRole, false);
              } else {
                if (typeof Toast !== 'undefined') Toast.error('Error', res && res.message ? res.message : 'Could not accept');
                detailAcceptBtn.disabled = false;
                detailAcceptBtn.textContent = typeof I18n !== 'undefined' ? I18n.t('chat.accept') : 'Accept';
              }
            } catch (err) {
              detailAcceptBtn.disabled = false;
              detailAcceptBtn.textContent = typeof I18n !== 'undefined' ? I18n.t('chat.accept') : 'Accept';
            }
          };
        }

        if (textarea) {
          textarea.disabled = true;
          textarea.placeholder = typeof I18n !== 'undefined' ? I18n.t('chat.acceptToReply') : 'Accept the conversation request above to reply.';
        }
        if (sendBtn) {
          sendBtn.disabled = true;
        }
      } else if (isUser) {
        if (!statusBanner) {
          statusBanner = document.createElement('div');
          statusBanner.id = 'chat-status-banner';
          statusBanner.className = 'card';
          statusBanner.style.cssText = 'background: #eff6ff; border: 1px solid #3b82f6; padding: var(--space-3); border-radius: var(--radius-md); margin-bottom: var(--space-3); text-align: center;';
          stream.parentNode.insertBefore(statusBanner, stream);
        }
        var custBannerText = typeof I18n !== 'undefined' ? I18n.t('chat.customerPendingBanner') : 'Conversation request sent. Waiting for vendor to accept.';
        statusBanner.innerHTML = '<span style="font-size: var(--font-size-sm); color: #1e40af; font-weight: 500;">⏳ ' + escapeHtml(custBannerText) + '</span>';

        if (textarea) {
          textarea.disabled = false;
          textarea.placeholder = typeof I18n !== 'undefined' ? I18n.t('chat.typeMessage') : 'Type a message...';
        }
        if (sendBtn) {
          sendBtn.disabled = false;
        }
      }
    } else {
      if (statusBanner) {
        statusBanner.remove();
      }
      if (textarea) {
        textarea.disabled = false;
        textarea.placeholder = typeof I18n !== 'undefined' ? I18n.t('chat.typeMessage') : 'Type a message...';
      }
      if (sendBtn) {
        sendBtn.disabled = false;
      }
    }

    // In polling mode: skip re-render if count unchanged
    if (silent && totalMsgs === _lastMsgCount) return;

    _lastMsgCount = totalMsgs;

    if (msgs.length === 0) {
      var noMsgText = typeof I18n !== 'undefined'
        ? I18n.t('chat.noMessagesYet')
        : 'No messages yet. Send a message to start the conversation.';
      stream.innerHTML = '<p class="no-messages">' + escapeHtml(noMsgText) + '</p>';
    } else {
      stream.innerHTML = msgs.map(function(msg) { return renderSingleMessage(msg); }).join('');
      updateLangBanner(msgs);
    }

    // Auto-scroll to bottom (newest message)
    stream.scrollTop = stream.scrollHeight;

  } catch (err) {
    console.error('[Chat] Error loading messages:', err);
    if (!silent) {
      var errMsg2  = typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Unable to load messages.';
      var backBtn3 = typeof I18n !== 'undefined' ? I18n.t('chat.backToMessages') : 'Back to messages';
      stream.innerHTML =
        '<div class="empty-state">' +
          '<p>' + escapeHtml(errMsg2) + '</p>' +
          '<a href="' + getChatUrl() + '" class="btn btn--primary btn--sm" style="margin-top:0.75rem;">' + escapeHtml(backBtn3) + '</a>' +
        '</div>';
    }
  }
}

/**
 * Renders a single message bubble with full translation UI.
 *
 * Display rules:
 *  - isOwnMessage === true  -> show originalMessage in own (right-aligned) bubble
 *  - isOwnMessage === false -> show translatedMessage (if available) in partner bubble
 *    with globe badge + "View original" toggle
 *  - If translation FAILED/UNAVAILABLE, show original with small indicator
 *  - If NOT_REQUIRED (same language), show message without badge
 */
function renderSingleMessage(msg) {
  if (!msg) return '';

  var isOwn      = !!msg.isOwnMessage;
  var status     = msg.translationStatus;
  var original   = msg.originalMessage || msg.message || '';
  var translated = msg.translatedMessage || original;
  var srcLang    = msg.sourceLanguage;

  var time = msg.timestamp
    ? new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : '';

  if (isOwn) {
    // Sender: always shows own original message
    return (
      '<div class="message own">' +
        '<div class="message-content">' + escapeHtml(original) + '</div>' +
        '<div class="message-meta">' + time + '</div>' +
      '</div>'
    );
  }

  // Receiver: show translated or fallback
  var isActuallyTranslated = status === 'TRANSLATED'
    && translated
    && original
    && translated !== original;

  if (isActuallyTranslated) {
    var langNames = { en: 'English', hi: 'Hindi', kn: 'Kannada' };
    var langKey   = (srcLang && srcLang.toLowerCase()) || 'kn';
    var langName  = (typeof I18n !== 'undefined' && I18n.LANGUAGES && I18n.LANGUAGES[langKey])
      ? (I18n.LANGUAGES[langKey].name || I18n.LANGUAGES[langKey].nativeName)
      : (langNames[langKey] || langKey);

    var translatedFromText = typeof I18n !== 'undefined'
      ? I18n.t('chat.translatedFrom', { lang: langName })
      : 'Translated from ' + langName;
    var viewOriginalText = typeof I18n !== 'undefined'
      ? I18n.t('chat.viewOriginal')
      : 'View original';

    return (
      '<div class="message partner"' +
          ' data-original="' + escapeAttr(original) + '"' +
          ' data-translated="' + escapeAttr(translated) + '">' +
        '<div class="message-content">' + escapeHtml(translated) + '</div>' +
        '<div class="translation-meta">' +
          '<span class="translation-badge">\uD83C\uDF10 ' + escapeHtml(translatedFromText) + '</span>' +
          '<button type="button"' +
                  ' class="btn-toggle-original"' +
                  ' data-showing-original="false"' +
                  ' aria-label="' + escapeAttr(viewOriginalText) + '">' +
            escapeHtml(viewOriginalText) +
          '</button>' +
        '</div>' +
        '<div class="message-meta">' + time + '</div>' +
      '</div>'
    );
  }

  // Translation failed / unavailable
  if (status === 'FAILED' || status === 'UNAVAILABLE') {
    var unavailText = typeof I18n !== 'undefined'
      ? I18n.t('chat.translationUnavailable')
      : 'Translation unavailable';
    return (
      '<div class="message partner">' +
        '<div class="message-content">' + escapeHtml(original) + '</div>' +
        '<div class="translation-meta">' +
          '<span class="translation-badge translation-badge--warn">\u26A0 ' + escapeHtml(unavailText) + '</span>' +
        '</div>' +
        '<div class="message-meta">' + time + '</div>' +
      '</div>'
    );
  }

  // Same language (NOT_REQUIRED) or legacy message (no status)
  var displayContent = translated || original;
  return (
    '<div class="message partner">' +
      '<div class="message-content">' + escapeHtml(displayContent) + '</div>' +
      '<div class="message-meta">' + time + '</div>' +
    '</div>'
  );
}

/**
 * Updates the language context banner at the top of the chat window.
 * Shows: "Globe Auto-translating between English and Kannada"
 * Only shows if at least one translated message exists.
 */
function updateLangBanner(msgs) {
  var banner = document.getElementById('chat-lang-banner');
  if (!banner) return;

  var translatedMsg = null;
  for (var i = 0; i < msgs.length; i++) {
    if (msgs[i].translationStatus === 'TRANSLATED') {
      translatedMsg = msgs[i];
      break;
    }
  }

  if (!translatedMsg) {
    banner.style.display = 'none';
    return;
  }

  var langNames2 = { en: 'English', hi: 'Hindi', kn: 'Kannada' };
  var src = translatedMsg.sourceLanguage || 'en';
  var tgt = translatedMsg.targetLanguage || 'en';

  var srcName = (typeof I18n !== 'undefined' && I18n.LANGUAGES && I18n.LANGUAGES[src])
    ? (I18n.LANGUAGES[src].name || I18n.LANGUAGES[src].nativeName)
    : (langNames2[src] || src);
  var tgtName = (typeof I18n !== 'undefined' && I18n.LANGUAGES && I18n.LANGUAGES[tgt])
    ? (I18n.LANGUAGES[tgt].name || I18n.LANGUAGES[tgt].nativeName)
    : (langNames2[tgt] || tgt);

  banner.style.display = 'flex';
  banner.textContent   = '\uD83C\uDF10 Auto-translating between ' + srcName + ' and ' + tgtName;
}


// =======================================================================
// UNREAD BADGE
// =======================================================================

function updateUnreadBadge(conversations) {
  var unreadCount = 0;
  if (conversations && Array.isArray(conversations)) {
    conversations.forEach(function(conv) {
      if (conv && conv.unreadCount !== undefined) {
        unreadCount += conv.unreadCount;
      }
    });
  }

  var badgeEl = document.querySelector('.unread-badge');
  if (badgeEl) {
    if (unreadCount > 0) {
      badgeEl.textContent   = unreadCount;
      badgeEl.style.display = 'block';
    } else {
      badgeEl.style.display = 'none';
    }
  }
}

async function refreshUnreadBadge() {
  try {
    var fn = window.API.getChatUnreadCount
      ? window.API.getChatUnreadCount.bind(window.API)
      : window.API.getUnreadCount.bind(window.API);
    var result = await fn();
    if (result && result.success && result.data) {
      var count = result.data.count || 0;
      var badgeEl2 = document.querySelector('.unread-badge');
      if (badgeEl2) {
        if (count > 0) {
          badgeEl2.textContent   = count;
          badgeEl2.style.display = 'block';
        } else {
          badgeEl2.style.display = 'none';
        }
      }
    }
  } catch (err) {
    console.error('[Chat] Error refreshing unread count:', err);
  }
}


// =======================================================================
// UTILITY HELPERS
// =======================================================================

/**
 * Escapes HTML special characters to prevent XSS injection.
 * Used for all user-supplied content inserted into innerHTML.
 */
function escapeHtml(text) {
  if (text === null || text === undefined) return '';
  var div = document.createElement('div');
  div.textContent = String(text);
  return div.innerHTML;
}

/**
 * Escapes a string for safe use inside an HTML attribute value.
 */
function escapeAttr(text) {
  if (text === null || text === undefined) return '';
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

// Legacy: backward-compatible wrapper around renderSingleMessage
function renderMessagePreview(conversation) {
  return renderSingleMessage({
    isOwnMessage:      !!conversation.lastMessageIsOwn,
    originalMessage:   conversation.originalMessage || conversation.lastMessage,
    translatedMessage: conversation.translatedMessage,
    translationStatus: conversation.translationStatus,
    sourceLanguage:    conversation.sourceLanguage,
    targetLanguage:    conversation.targetLanguage,
    timestamp:         conversation.lastMessageTimestamp,
    message:           conversation.lastMessage,
  });
}

// Legacy: backward-compatible alias
function loadConversation(chatId, partnerName, partnerRole) {
  return loadAndRenderThread(chatId, partnerName, partnerRole, false);
}
