/**
 * Maitri — API Client
 *
 * Thin fetch() wrapper. Phase 2 uses this only to call /api/health.
 * In Phase 3+ all auth/vendor/review endpoints will be called through here.
 */

const API = {
  /**
   * Generic GET request.
   * @param {string} path - e.g. '/health'
   * @returns {Promise<object>} Parsed ApiResponse JSON
   */
  async get(path) {
    const url = `${CONFIG.API_BASE_URL}${path}`;
    const res = await fetch(url, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
    });
    return res.json();
  },

  /**
   * Generic POST request.
   * @param {string} path
   * @param {object} body
   * @param {boolean} [auth=false] - include JWT token if true
   * @returns {Promise<object>}
   */
  async post(path, body, auth = false) {
    const url = `${CONFIG.API_BASE_URL}${path}`;
    const headers = { 'Content-Type': 'application/json' };
    if (auth) {
      const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
      if (token) headers['Authorization'] = `Bearer ${token}`;
    }
    const res = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    });
    return res.json();
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
