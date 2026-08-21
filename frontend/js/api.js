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

  // ── User Profile (Phase 6) ─────────────────────────────────────

  /** Authenticated USER/ADMIN fetches their own editable profile. */
  getUserProfile() {
    return this.request('/users/me', { auth: true });
  },

  /** Authenticated USER/ADMIN updates their own editable profile. */
  updateUserProfile(payload) {
    return this.request('/users/me', { method: 'PUT', body: payload, auth: true });
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
