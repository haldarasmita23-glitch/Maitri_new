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
