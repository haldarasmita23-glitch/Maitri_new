/**
 * Maitri — Configuration
 * Central place for API URLs and app-wide constants.
 */

const CONFIG = {
  /**
   * Backend API base URL.
   *
   * Resolution Order:
   *   1. Explicit override via window.MAITRI_API_BASE_URL
   *   2. Local development fallback
   *   3. Production Render backend
   */
  get API_BASE_URL() {
    // 1. Explicit override
    // This allows the API URL to be changed from index.html
    // or another deployment configuration without editing this file.
    if (
      typeof window !== 'undefined' &&
      window.MAITRI_API_BASE_URL
    ) {
      return window.MAITRI_API_BASE_URL;
    }

    // 2. Local development
    // When running the frontend locally, use the local Spring Boot backend.
    if (
      typeof window !== 'undefined' &&
      (
        window.location.hostname === 'localhost' ||
        window.location.hostname === '127.0.0.1'
      )
    ) {
      return 'http://localhost:8080/api';
    }

    // 3. Production
    // When deployed (for example, on Vercel), use the Render backend.
    return 'https://maitri-backend-ivv6.onrender.com/api';
  },

  /** App name */
  APP_NAME: 'Maitri',

  /** App version */
  VERSION: '1.0.0',

  /** Location served */
  LOCATION: 'Peenya / Nagasandra, Bengaluru',

  /** V1 categories */
  CATEGORIES: [
    'Street Food',
    'Tailors',
    'Printing & Xerox',
    'Mobile/Laptop Repair'
  ],

  /** Local storage keys */
  STORAGE_KEYS: {
    AUTH_TOKEN: 'maitri_auth_token',
    USER_DATA: 'maitri_user_data',
    FAVOURITES: 'maitri_favourites',
  },
};

// Freeze configuration to prevent accidental mutations.
Object.freeze(CONFIG);
Object.freeze(CONFIG.STORAGE_KEYS);