/**
 * Maitri — API Client
 *
 * Thin fetch() wrapper. Auth endpoints return HTTP status alongside the
 * standard API response so forms can display backend validation messages.
 */

const API = {
  async request(path, { method = 'GET', body, auth = false } = {}) {
    const headers = { 'Content-Type': 'application/json' };
    if (auth) {
      const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
      if (token) headers.Authorization = `Bearer ${token}`;
    }

    const response = await fetch(`${CONFIG.API_BASE_URL}${path}`, {
      method,
      headers,
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    });

    let data = null;
    try {
      data = await response.json();
    } catch {
      data = { success: false, message: 'The server returned an invalid response.' };
    }

    return { ok: response.ok, status: response.status, data };
  },

  /**
   * Generic GET request.
   * @param {string} path - e.g. '/health'
   * @returns {Promise<object>} Parsed ApiResponse JSON
   */
  async get(path, auth = false) {
    const result = await this.request(path, { auth });
    return result.data;
  },

  /**
   * Generic POST request.
   * @param {string} path
   * @param {object} body
   * @param {boolean} [auth=false] - include JWT token if true
   * @returns {Promise<object>}
   */
  async post(path, body, auth = false) {
    const result = await this.request(path, { method: 'POST', body, auth });
    return result.data;
  },

  /**
   * Generic PUT request.
   * @param {string} path
   * @param {object} body
   * @param {boolean} [auth=false] - include JWT token if true
   * @returns {Promise<object>}
   */
  async put(path, body, auth = false) {
    const result = await this.request(path, { method: 'PUT', body, auth });
    return result.data;
  },

  login(credentials) {
    return this.request('/auth/login', { method: 'POST', body: credentials });
  },

  register(account) {
    return this.request('/auth/register', { method: 'POST', body: account });
  },

  getCurrentUser() {
    return this.request('/auth/me', { auth: true });
  },

  // ── Vendors (Phase 5) ─────────────────────────────────────────

  /**
   * Approved vendors, optionally filtered by category slug and search text.
   * @returns {Promise<object>} Parsed ApiResponse (list lives at .data)
   */
  async getVendors({ category, q } = {}) {
    const params = new URLSearchParams();
    if (category) params.set('category', category);
    if (q) params.set('q', q);
    const query = params.toString();
    return this.get(`/vendors${query ? `?${query}` : ''}`);
  },

  /** Single approved vendor detail. @returns {Promise<object>} ApiResponse */
  async getVendor(id) {
    return this.get(`/vendors/${encodeURIComponent(id)}`);
  },

  /** Authenticated VENDOR submits a business listing (→ PENDING). */
  async applyVendor(payload) {
    return this.post('/vendors/apply', payload, true);
  },

  /** Authenticated VENDOR fetches their own listing. */
  getMyVendor() {
    return this.request('/vendors/me', { auth: true });
  },

  /** Authenticated VENDOR updates their own listing. */
  updateMyVendor(payload) {
    return this.request('/vendors/me', { method: 'PUT', body: payload, auth: true });
  },

  /** ADMIN: pending vendor review queue. */
  getAdminPendingVendors() {
    return this.request('/vendors/admin/pending', { auth: true });
  },

  /** ADMIN: approve a vendor. */
  approveVendor(id) {
    return this.request(`/vendors/${encodeURIComponent(id)}/approve`, { method: 'PATCH', auth: true });
  },

  /** ADMIN: reject a vendor. */
  rejectVendor(id) {
    return this.request(`/vendors/${encodeURIComponent(id)}/reject`, { method: 'PATCH', auth: true });
  },

  // ── User Profile & Preferences ───────────────────────────────

  /** Authenticated USER/ADMIN fetches their own editable profile. */
  getUserProfile() {
    return this.request('/users/me', { auth: true });
  },

  /** Authenticated USER/ADMIN updates their own editable profile. */
  updateUserProfile(payload) {
    return this.request('/users/me', { method: 'PUT', body: payload, auth: true });
  },

  /** Authenticated USER/ADMIN/VENDOR fetches their user preferences. */
  getUserPreferences() {
    return this.request('/users/preferences', { auth: true });
  },

  /** Authenticated USER/ADMIN/VENDOR updates their language preference ("en", "hi", "kn"). */
  updateLanguagePreference(language) {
    return this.request('/users/preferences/language', {
      method: 'PUT',
      body: { language },
      auth: true,
    });
  },

  // ── Reviews (Phase 7) ──────────────────────────────────────────

  /** Authenticated USER submits a review for a vendor. */
  submitReview(payload) {
    return this.request('/reviews', { method: 'POST', body: payload, auth: true });
  },

  /** PUBLIC: Get paginated reviews for a vendor. */
  getVendorReviews(vendorId, page = 0, size = 10) {
    return this.get(`/reviews/vendor/${encodeURIComponent(vendorId)}?page=${page}&size=${size}`);
  },

  /** PUBLIC: Get rating summary for a vendor. */
  getVendorRatingSummary(vendorId) {
    return this.get(`/reviews/vendor/${encodeURIComponent(vendorId)}/summary`);
  },

  /** Authenticated USER: Get their own reviews. */
  getMyReviews() {
    return this.request('/reviews/my', { auth: true });
  },

  /** Authenticated USER: Update their own review. */
  updateReview(reviewId, payload) {
    return this.request(`/reviews/${encodeURIComponent(reviewId)}`, { method: 'PUT', body: payload, auth: true });
  },

  /** Authenticated USER: Delete their own review. */
  deleteReview(reviewId) {
    return this.request(`/reviews/${encodeURIComponent(reviewId)}`, { method: 'DELETE', auth: true });
  },

  // ── Favourites (Phase 8) ────────────────────────────────────────

  /** Authenticated USER/ADMIN: Get their own favourites (with vendor details). */
  getFavourites() {
    return this.request('/favourites', { auth: true });
  },

  /** Authenticated USER/ADMIN: Add an approved vendor to favourites. */
  addFavourite(vendorId) {
    return this.request('/favourites', { method: 'POST', body: { vendorId }, auth: true });
  },

  /** Authenticated USER/ADMIN: Remove a vendor from favourites. */
  removeFavourite(vendorId) {
    return this.request(`/favourites/${encodeURIComponent(vendorId)}`, { method: 'DELETE', auth: true });
  },

  /** Authenticated USER/ADMIN: Check whether a vendor is favourited. */
  isFavourite(vendorId) {
    return this.request(`/favourites/${encodeURIComponent(vendorId)}`, { auth: true });
  },

  // ── Complaints (Phase 9) ───────────────────────────────────────

  /** Authenticated USER/ADMIN: Get their own complaints (with vendor details). */
  getMyComplaints() {
    return this.request('/complaints/my', { auth: true });
  },

  /** Authenticated USER/ADMIN: Get one of their own complaints. */
  getComplaint(id) {
    return this.request(`/complaints/${encodeURIComponent(id)}`, { auth: true });
  },

  /** Authenticated USER/ADMIN: Raise a complaint against an approved vendor. */
  createComplaint(payload) {
    return this.request('/complaints', { method: 'POST', body: payload, auth: true });
  },

  /** Authenticated USER/ADMIN: Edit their own PENDING complaint. */
  updateComplaint(id, payload) {
    return this.request(`/complaints/${encodeURIComponent(id)}`, { method: 'PUT', body: payload, auth: true });
  },

  /** Authenticated USER/ADMIN: Delete their own PENDING complaint. */
  deleteComplaint(id) {
    return this.request(`/complaints/${encodeURIComponent(id)}`, { method: 'DELETE', auth: true });
  },

  /** Authenticated VENDOR/ADMIN: Get complaints about the vendor's own business. */
  getVendorComplaints() {
    return this.request('/complaints/vendor/me', { auth: true });
  },

  /** Authenticated VENDOR/ADMIN: Update a complaint's status (shared endpoint).
   *  VENDOR transitions are scoped to their own business; ADMIN may do any transition. */
  updateComplaintStatus(id, status) {
    return this.request(`/complaints/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status }, auth: true });
  },

  /** ADMIN: Get all complaints, optionally filtered by status. */
  getAdminComplaints(status) {
    const query = status ? `?status=${encodeURIComponent(status)}` : '';
    return this.request(`/complaints/admin${query}`, { auth: true });
  },

  /** ADMIN: Set/update the internal admin note on a complaint. */
  setAdminNote(id, adminNote) {
    return this.request(`/complaints/${encodeURIComponent(id)}/note`, { method: 'PATCH', body: { adminNote }, auth: true });
  },

  // ── Notifications (Phase 10) ──────────────────────────────────────

  /** Authenticated USER/VENDOR/ADMIN: Get their own notifications, newest first. */
  async getNotifications() {
    return this.get('/notifications', true);
  },

  /** Authenticated USER/VENDOR/ADMIN: Get their unread notification count. */
  async getUnreadCount() {
    return this.get('/notifications/unread-count', true);
  },

  async getNotificationUnreadCount() {
    return this.get('/notifications/unread-count', true);
  },

  /** Authenticated USER/VENDOR/ADMIN: Mark one notification as read. */
  async markNotificationRead(id) {
    return this.put(`/notifications/${encodeURIComponent(id)}/read`, null, true);
  },

  /** Authenticated USER/VENDOR/ADMIN: Mark all notifications as read. */
  async markAllNotificationsRead() {
    return this.put('/notifications/read-all', null, true);
  },

  // ── Chat / Contact (Phase 11) ──────────────────────────────────────
  // NOTE: CONFIG.API_BASE_URL already ends with "/api", so these paths
  // must NOT include the "/api" prefix (avoids /api/api/... URLs).

  /** Authenticated USER/VENDOR/ADMIN: List their conversations. */
  async getChats() {
    return this.get('/chats', true);
  },

  /** Authenticated participant: Get a single chat/message by ID. */
  async getChat(chatId) {
    return this.get(`/chats/${encodeURIComponent(chatId)}`, true);
  },

  /** Authenticated USER/VENDOR/ADMIN: Start a conversation with a receiver. */
  async startConversation(receiverId, receiverRole) {
    return this.post('/chats', { receiverId, receiverRole }, true);
  },

  /** Authenticated participant: Send a new message (TEXT or IMAGE). */
  async sendMessage(chatId, payload) {
    return this.post(`/chats/${encodeURIComponent(chatId)}/messages`, payload, true);
  },

  /** Authenticated VENDOR/ADMIN: Accept a pending conversation request. */
  async acceptConversation(chatId) {
    return this.put(`/chats/${encodeURIComponent(chatId)}/accept`, null, true);
  },

  /** Authenticated participant: Mark a conversation as read. */
  async markChatRead(chatId) {
    return this.put(`/chats/${encodeURIComponent(chatId)}/read`, null, true);
  },

  /** Authenticated USER/VENDOR/ADMIN: Get total unread message count. */
  async getChatUnreadCount() {
    return this.get('/chats/unread-count', true);
  },

  /**
   * Authenticated participant: Get the full message thread for a conversation.
   * Returns a Page<ChatMessageResponse> (newest first — reverse before rendering).
   *
   * @param {string} chatId - The partner's account ID
   * @param {number} [page=0] - Zero-based page number
   * @param {number} [size=30] - Number of messages to fetch (max 50)
   * @returns {Promise<object>} ApiResponse wrapping a Spring Page
   */
  async getChatMessages(chatId, page = 0, size = 30) {
    return this.get(
      `/chats/${encodeURIComponent(chatId)}/messages?page=${page}&size=${size}`,
      true
    );
  },

  // ── NLP / Contact (Phase 13) ──────────────────────────────────────
  // NOTE: CONFIG.API_BASE_URL already ends with "/api", so these paths
  // must NOT include the "/api" prefix (avoids /api/api/... URLs).

  /** USER|ADMIN: Analyze free-form text for sentiment, keywords, aspects. */
  async analyzeText(text, maxKeywords = 10) {
    return this.request('/nlp/analyze', {
      method: 'POST',
      body: { text, maxKeywords },
      auth: true,
    });
  },

  /** USER|ADMIN: Analyze an existing review by ID. */
  async analyzeReview(reviewId) {
    return this.request(`/nlp/review/${encodeURIComponent(reviewId)}`, {
      method: 'POST',
      auth: true,
    });
  },

  /** USER|ADMIN: Aggregate NLP insights across all reviews for a vendor. */
  async getVendorInsights(vendorId, page = 0, size = 10) {
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('size', size);
    return this.get(`/nlp/reviews/vendor/${encodeURIComponent(vendorId)}?${params.toString()}`, {
      auth: true,
    });
  },

  /**
   * Check if the backend is reachable.
   * @returns {Promise<{ok: boolean, data?: object, error?: string}>}
   */
  async checkHealth() {
    try {
      const data = await this.get('/health');
      return { ok: data.success === true, data };
    } catch (err) {
      return { ok: false, error: err.message };
    }
  },
};

// Make API globally available for classic script consumers (e.g. chat.js)
// that cannot use ES module imports.
window.API = API;
