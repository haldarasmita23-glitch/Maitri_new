/**
 * Maitri — Configuration
 * Central place for API URLs and app-wide constants.
 */

const CONFIG = {
  /**
   * Backend API base URL.
   *
   * Resolution order:
   * 1. window.MAITRI_API_BASE_URL
   * 2. localhost during local development
   * 3. production backend URL
   */
  get API_BASE_URL() {

    // 1. Explicit override
    if (
      typeof window !== 'undefined' &&
      window.MAITRI_API_BASE_URL
    ) {
      return window.MAITRI_API_BASE_URL;
    }

    // 2. Local development
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
    return 'https://maitri-backend-ivv6.onrender.com/api';
  },

  APP_NAME: 'Maitri',

  VERSION: '1.0.0',

  LOCATION: 'Peenya / Nagasandra, Bengaluru',

  CATEGORIES: [
    'Street Food',
    'Tailors',
    'Printing & Xerox',
    'Mobile/Laptop Repair'
  ],

  SUPPORTED_LANGUAGES: ['en', 'hi', 'kn'],

  STORAGE_KEYS: {
    AUTH_TOKEN: 'maitri_auth_token',
    USER_DATA: 'maitri_user_data',
    FAVOURITES: 'maitri_favourites',
    LANGUAGE: 'maitri_language',
  },
};

Object.freeze(CONFIG);
Object.freeze(CONFIG.STORAGE_KEYS);