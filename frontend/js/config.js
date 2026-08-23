/**
 * Maitri — Configuration
 * Central place for API URLs and app-wide constants.
 */

const CONFIG = {
  /**
   * Backend API base URL.
   * In production, this can be overridden by setting window.MAITRI_API_BASE_URL
   * before this script loads (e.g., via a meta tag or inline script in index.html).
   * Local default: http://localhost:8080/api
   * Production: https://<railway-domain>/api
   */
  get API_BASE_URL() {
    // Allow override via global variable set before this script loads
    if (typeof window !== 'undefined' && window.MAITRI_API_BASE_URL) {
      return window.MAITRI_API_BASE_URL;
    }
    // Local development default
    return 'http://localhost:8080/api';
  },

  /** App name */
  APP_NAME: 'Maitri',

  /** App version */
  VERSION: '1.0.0',

  /** Location served */
  LOCATION: 'Peenya / Nagasandra, Bengaluru',

  /** V1 categories */
  CATEGORIES: ['Street Food', 'Tailors', 'Printing & Xerox', 'Mobile/Laptop Repair'],

  /** Local storage keys */
  STORAGE_KEYS: {
    AUTH_TOKEN:  'maitri_auth_token',
    USER_DATA:   'maitri_user_data',
    FAVOURITES:  'maitri_favourites',
  },
};

// Freeze to prevent accidental mutations
Object.freeze(CONFIG);
Object.freeze(CONFIG.STORAGE_KEYS);
