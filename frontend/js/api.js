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
