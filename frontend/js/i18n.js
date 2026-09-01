/**
 * Maitri — Multilingual Localization (i18n) Engine
 *
 * Provides centralized translation lookup, language preference persistence,
 * reactive DOM translation, and accessible language selectors for:
 *   - "en": English
 *   - "hi": हिन्दी (Hindi)
 *   - "kn": ಕನ್ನಡ (Kannada)
 */

const I18n = {
  LANGUAGES: {
    en: { code: 'en', name: 'English', nativeName: 'English' },
    hi: { code: 'hi', name: 'Hindi', nativeName: 'हिन्दी' },
    kn: { code: 'kn', name: 'Kannada', nativeName: 'ಕನ್ನಡ' },
  },

  DEFAULT_LANGUAGE: 'en',
  _currentLanguage: 'en',
  _initialized: false,

  /**
   * Determine initial language according to priority hierarchy:
   * 1. Saved user preference from account (if logged in)
   * 2. Saved local storage preference (maitri_language)
   * 3. Browser navigator language (if hi or kn)
   * 4. Default fallback: 'en'
   */
  detectLanguage() {
    // 1. Saved user account data
    try {
      if (typeof AuthSession !== 'undefined') {
        const u = AuthSession.user();
        if (u && u.preferredLanguage && this.LANGUAGES[u.preferredLanguage]) {
          return u.preferredLanguage;
        }
      }
      const storedUserKey = (typeof CONFIG !== 'undefined' && CONFIG.STORAGE_KEYS?.USER_DATA) || 'maitri_user_data';
      const rawUser = sessionStorage.getItem(storedUserKey) || localStorage.getItem(storedUserKey);
      const storedUser = JSON.parse(rawUser);
      if (storedUser && storedUser.preferredLanguage && this.LANGUAGES[storedUser.preferredLanguage]) {
        return storedUser.preferredLanguage;
      }
    } catch {
      // ignore parse errors
    }

    // 2. Saved session/local storage preference
    try {
      const langKey = (typeof CONFIG !== 'undefined' && CONFIG.STORAGE_KEYS?.LANGUAGE) || 'maitri_language';
      const savedLang = sessionStorage.getItem(langKey) || localStorage.getItem(langKey);
      if (savedLang && this.LANGUAGES[savedLang]) {
        return savedLang;
      }
    } catch {
      // ignore
    }

    // 3. Browser locale
    try {
      const browserLang = (navigator.language || navigator.userLanguage || '').toLowerCase();
      if (browserLang.startsWith('hi')) return 'hi';
      if (browserLang.startsWith('kn')) return 'kn';
    } catch {
      // ignore
    }

    // 4. Default fallback
    return this.DEFAULT_LANGUAGE;
  },

  /**
   * Initialise i18n engine on page load.
   */
  init() {
    if (this._initialized) return;
    this._currentLanguage = this.detectLanguage();
    document.documentElement.lang = this._currentLanguage;
    this.translatePage();
    this.initLanguageSelectors();
    this._initialized = true;

    // Listen to session changes to synchronize account language
    window.addEventListener('maitri:auth-change', event => {
      const user = event.detail;
      if (user && user.preferredLanguage && this.LANGUAGES[user.preferredLanguage]) {
        if (user.preferredLanguage !== this._currentLanguage) {
          this.setLanguage(user.preferredLanguage, false);
        }
      }
    });
  },

  /**
   * Get active language code ("en", "hi", "kn").
   */
  getLanguage() {
    return this._currentLanguage;
  },

  /**
   * Change active language, persist to storage & backend, and translate UI.
   * @param {string} lang - "en", "hi", or "kn"
   * @param {boolean} [syncBackend=true] - whether to update backend user profile if logged in
   */
  async setLanguage(lang, syncBackend = true) {
    if (!this.LANGUAGES[lang]) {
      console.warn(`[I18n] Unsupported language "${lang}", falling back to English.`);
      lang = this.DEFAULT_LANGUAGE;
    }

    this._currentLanguage = lang;
    document.documentElement.lang = lang;

    // Persist in sessionStorage (tab) and localStorage
    try {
      const langKey = (typeof CONFIG !== 'undefined' && CONFIG.STORAGE_KEYS?.LANGUAGE) || 'maitri_language';
      sessionStorage.setItem(langKey, lang);
      localStorage.setItem(langKey, lang);
    } catch {
      // ignore
    }

    // Update stored user preference if logged in
    try {
      const userKey = (typeof CONFIG !== 'undefined' && CONFIG.STORAGE_KEYS?.USER_DATA) || 'maitri_user_data';
      const rawUser = sessionStorage.getItem(userKey) || localStorage.getItem(userKey);
      const storedUser = JSON.parse(rawUser);
      if (storedUser) {
        storedUser.preferredLanguage = lang;
        sessionStorage.setItem(userKey, JSON.stringify(storedUser));
        localStorage.setItem(userKey, JSON.stringify(storedUser));
      }
    } catch {
      // ignore
    }

    // Sync with backend API if user is logged in
    if (syncBackend && typeof API !== 'undefined') {
      const tokenKey = (typeof CONFIG !== 'undefined' && CONFIG.STORAGE_KEYS?.AUTH_TOKEN) || 'maitri_auth_token';
      const token = sessionStorage.getItem(tokenKey) || localStorage.getItem(tokenKey);
      if (token) {
        try {
          if (typeof API.updateLanguagePreference === 'function') {
            await API.updateLanguagePreference(lang);
          } else if (typeof API.updateUserProfile === 'function') {
            const userKey = (typeof CONFIG !== 'undefined' && CONFIG.STORAGE_KEYS?.USER_DATA) || 'maitri_user_data';
            const user = JSON.parse(sessionStorage.getItem(userKey) || localStorage.getItem(userKey)) || {};
            if (user.name) {
              await API.updateUserProfile({ ...user, preferredLanguage: lang });
            }
          }
        } catch (err) {
          console.warn('[I18n] Could not sync language preference with backend:', err);
        }
      }
    }

    // Update DOM translations
    this.translatePage();
    this.updateLanguageSelectors();

    // Dispatch global event for reactive components (lists, cards, charts)
    window.dispatchEvent(new CustomEvent('maitri:language-change', {
      detail: { language: lang }
    }));
  },

  /**
   * Translate a key with optional parameter interpolation.
   * Example: t('common.appName') or t('home.vendorsCount', { count: 12 })
   *
   * @param {string} key - e.g. "common.home"
   * @param {object} [params={}] - interpolation values
   * @returns {string} translated text or fallback
   */
  t(key, params = {}) {
    if (!key) return '';

    const lang = this._currentLanguage;
    const translations = (typeof window !== 'undefined' && window.TRANSLATIONS) || {};

    let value = this._lookupKey(translations[lang], key);

    // Fallback to English if missing in current language
    if (value === undefined && lang !== this.DEFAULT_LANGUAGE) {
      value = this._lookupKey(translations[this.DEFAULT_LANGUAGE], key);
    }

    // Fallback to key itself if not found
    if (value === undefined) {
      return key;
    }

    // Parameter substitution: "{name}" → params.name
    if (params && typeof params === 'object') {
      return Object.keys(params).reduce((str, paramKey) => {
        return str.replace(new RegExp(`\\{${paramKey}\\}`, 'g'), params[paramKey]);
      }, String(value));
    }

    return String(value);
  },

  /**
   * Resolves a dotted key path like "home.hero.title" from an object.
   */
  _lookupKey(obj, path) {
    if (!obj || !path) return undefined;
    const parts = path.split('.');
    let current = obj;
    for (const part of parts) {
      if (current && typeof current === 'object' && part in current) {
        current = current[part];
      } else {
        return undefined;
      }
    }
    return current;
  },

  /**
   * Scan and translate all DOM elements with data-i18n-* attributes.
   * @param {HTMLElement|Document} [root=document]
   */
  translatePage(root = document) {
    if (!root || !root.querySelectorAll) return;

    // 1. Text content
    root.querySelectorAll('[data-i18n]').forEach(el => {
      const key = el.getAttribute('data-i18n');
      if (key) {
        const translated = this.t(key);
        if (translated) el.textContent = translated;
      }
    });

    // 2. HTML content (for translations containing <strong>, <a>, <br>)
    root.querySelectorAll('[data-i18n-html]').forEach(el => {
      const key = el.getAttribute('data-i18n-html');
      if (key) {
        const translated = this.t(key);
        if (translated) el.innerHTML = translated;
      }
    });

    // 3. Input placeholder
    root.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
      const key = el.getAttribute('data-i18n-placeholder');
      if (key) {
        const translated = this.t(key);
        if (translated) el.setAttribute('placeholder', translated);
      }
    });

    // 4. Accessibility aria-label
    root.querySelectorAll('[data-i18n-aria-label]').forEach(el => {
      const key = el.getAttribute('data-i18n-aria-label');
      if (key) {
        const translated = this.t(key);
        if (translated) el.setAttribute('aria-label', translated);
      }
    });

    // 5. Tooltip title
    root.querySelectorAll('[data-i18n-title]').forEach(el => {
      const key = el.getAttribute('data-i18n-title');
      if (key) {
        const translated = this.t(key);
        if (translated) el.setAttribute('title', translated);
      }
    });
  },

  /**
   * Initialize and attach language selectors across the page.
   */
  initLanguageSelectors() {
    this.createNavbarSelector();
    this.createMobileSelector();
    this.updateLanguageSelectors();
  },

  /**
   * Creates or mounts desktop navbar language selector.
   */
  createNavbarSelector() {
    const navbars = document.querySelectorAll('.navbar__inner');
    navbars.forEach(navbar => {
      if (navbar.querySelector('.lang-selector-desktop')) return;

      const selectorWrapper = document.createElement('div');
      selectorWrapper.className = 'lang-selector-desktop';
      selectorWrapper.setAttribute('aria-label', this.t('common.language'));

      selectorWrapper.innerHTML = `
        <div class="lang-dropdown">
          <button type="button" class="lang-btn btn btn--ghost btn--sm" aria-expanded="false" aria-haspopup="listbox" aria-label="${this.t('common.selectLanguage')}">
            <span class="lang-globe" aria-hidden="true">🌐</span>
            <span class="lang-current-label">${this.LANGUAGES[this._currentLanguage].nativeName}</span>
            <svg class="lang-chevron" width="10" height="6" viewBox="0 0 10 6" fill="none" aria-hidden="true">
              <path d="M1 1L5 5L9 1" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
          <ul class="lang-menu" role="listbox" aria-label="${this.t('common.selectLanguage')}">
            <li role="option" data-lang="en" class="lang-option ${this._currentLanguage === 'en' ? 'active' : ''}" tabindex="0">
              <span class="lang-option__name">English</span>
            </li>
            <li role="option" data-lang="hi" class="lang-option ${this._currentLanguage === 'hi' ? 'active' : ''}" tabindex="0">
              <span class="lang-option__name">हिन्दी</span>
            </li>
            <li role="option" data-lang="kn" class="lang-option ${this._currentLanguage === 'kn' ? 'active' : ''}" tabindex="0">
              <span class="lang-option__name">ಕನ್ನಡ</span>
            </li>
          </ul>
        </div>
      `;

      const btn = selectorWrapper.querySelector('.lang-btn');
      const menu = selectorWrapper.querySelector('.lang-menu');

      // Toggle dropdown
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const isOpen = menu.classList.toggle('open');
        btn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
      });

      // Select language
      selectorWrapper.querySelectorAll('.lang-option').forEach(opt => {
        const choose = () => {
          const lang = opt.dataset.lang;
          this.setLanguage(lang);
          menu.classList.remove('open');
          btn.setAttribute('aria-expanded', 'false');
        };
        opt.addEventListener('click', choose);
        opt.addEventListener('keydown', e => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            choose();
          }
        });
      });

      // Close on outside click
      document.addEventListener('click', () => {
        menu.classList.remove('open');
        btn.setAttribute('aria-expanded', 'false');
      });

      // Insert before auth container or at end of inner navbar
      const authArea = navbar.querySelector('.navbar__auth');
      const hamburger = navbar.querySelector('.navbar__hamburger');
      if (authArea) {
        navbar.insertBefore(selectorWrapper, authArea);
      } else if (hamburger) {
        navbar.insertBefore(selectorWrapper, hamburger);
      } else {
        navbar.appendChild(selectorWrapper);
      }
    });
  },

  /**
   * Creates or mounts mobile drawer language selector.
   */
  createMobileSelector() {
    const mobileMenus = document.querySelectorAll('.navbar__mobile');
    mobileMenus.forEach(menu => {
      if (menu.querySelector('.lang-selector-mobile')) return;

      const mobileSelector = document.createElement('div');
      mobileSelector.className = 'lang-selector-mobile';
      mobileSelector.setAttribute('aria-label', this.t('common.language'));

      mobileSelector.innerHTML = `
        <div class="lang-mobile-label">
          <span aria-hidden="true">🌐</span> <span data-i18n="common.language">${this.t('common.language')}</span>
        </div>
        <div class="lang-mobile-pills" role="radiogroup" aria-label="${this.t('common.language')}">
          <button type="button" class="lang-pill ${this._currentLanguage === 'en' ? 'active' : ''}" data-lang="en">English</button>
          <button type="button" class="lang-pill ${this._currentLanguage === 'hi' ? 'active' : ''}" data-lang="hi">हिन्दी</button>
          <button type="button" class="lang-pill ${this._currentLanguage === 'kn' ? 'active' : ''}" data-lang="kn">ಕನ್ನಡ</button>
        </div>
      `;

      mobileSelector.querySelectorAll('.lang-pill').forEach(pill => {
        pill.addEventListener('click', () => {
          this.setLanguage(pill.dataset.lang);
        });
      });

      // Insert before mobile divider
      const divider = menu.querySelector('.navbar__mobile-divider');
      if (divider) {
        menu.insertBefore(mobileSelector, divider);
      } else {
        menu.prepend(mobileSelector);
      }
    });
  },

  /**
   * Update active classes and labels on language selectors when language changes.
   */
  updateLanguageSelectors() {
    const currentNative = this.LANGUAGES[this._currentLanguage]?.nativeName || 'English';

    document.querySelectorAll('.lang-current-label').forEach(label => {
      label.textContent = currentNative;
    });

    document.querySelectorAll('.lang-option').forEach(opt => {
      opt.classList.toggle('active', opt.dataset.lang === this._currentLanguage);
    });

    document.querySelectorAll('.lang-pill').forEach(pill => {
      pill.classList.toggle('active', pill.dataset.lang === this._currentLanguage);
    });
  },

  /**
   * Helper to translate category names (e.g. "Street Food" -> localized)
   */
  translateCategory(name) {
    if (!name) return '';
    const norm = String(name).toLowerCase().trim();
    if (norm.includes('street') || norm.includes('food')) return this.t('categories.streetFood');
    if (norm.includes('tailor')) return this.t('categories.tailors');
    if (norm.includes('print') || norm.includes('xerox')) return this.t('categories.printing');
    if (norm.includes('repair') || norm.includes('mobile') || norm.includes('laptop')) return this.t('categories.repair');
    return name;
  },

  /**
   * Helper to translate area names (e.g. "Peenya" -> localized)
   */
  translateArea(name) {
    if (!name) return '';
    const norm = String(name).toLowerCase().trim();
    if (norm === 'peenya') return this.t('common.peenya');
    if (norm.includes('1st stage')) return this.t('common.peenya1stStage');
    if (norm.includes('2nd stage')) return this.t('common.peenya2ndStage');
    if (norm.includes('industrial area')) return this.t('common.peenyaIndustrialArea');
    if (norm === 'nagasandra') return this.t('common.nagasandra');
    if (norm.includes('main road')) return this.t('common.nagasandraMainRoad');
    if (norm === 'jalahalli') return this.t('common.jalahalli');
    if (norm === 'bengaluru') return this.t('common.bengaluru');
    return name;
  }
};

// Global shorthand helper
window.t = function(key, params) {
  return I18n.t(key, params);
};

// Expose globally
window.I18n = I18n;

// Automatically initialize when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => I18n.init());
} else {
  I18n.init();
}
