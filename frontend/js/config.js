/**
 * Maitri — Configuration
 * Central place for API URLs and app-wide constants.
 */

const CONFIG = {
  /** Backend API base URL. Change this when deploying to production. */
  API_BASE_URL: 'http://localhost:8080/api',

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
