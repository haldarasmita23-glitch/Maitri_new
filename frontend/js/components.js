/**
 * Maitri — Shared Components
 *
 * Navbar interactions, toast notifications, modal management,
 * active link highlighting, scroll effects.
 */

// ── Toast System ──────────────────────────────────────────────────

const Toast = {
  container: null,

  init() {
    if (this.container) return;
    this.container = document.createElement('div');
    this.container.className = 'toast-container';
    this.container.setAttribute('aria-live', 'polite');
    document.body.appendChild(this.container);
  },

  show(title, message = '', type = 'info', duration = 4000) {
    this.init();

    const icons = { success: '✅', error: '❌', warning: '⚠️', info: 'ℹ️' };

    const toast = document.createElement('div');
    toast.className = `toast toast--${type}`;
    toast.setAttribute('role', 'alert');
    toast.innerHTML = `
      <span class="toast__icon" aria-hidden="true">${icons[type] || icons.info}</span>
      <div class="toast__body">
        <div class="toast__title">${title}</div>
        ${message ? `<div class="toast__message">${message}</div>` : ''}
      </div>
      <button class="toast__close" aria-label="Close notification">×</button>
    `;

    toast.querySelector('.toast__close').addEventListener('click', () => this.dismiss(toast));
    this.container.appendChild(toast);

    if (duration > 0) {
      setTimeout(() => this.dismiss(toast), duration);
    }

    return toast;
  },

  dismiss(toast) {
    if (!toast || !toast.parentNode) return;
    toast.classList.add('toast--exit');
    toast.addEventListener('animationend', () => toast.remove(), { once: true });
  },

  success(title, message = '') { return this.show(title, message, 'success'); },
  error(title, message = '')   { return this.show(title, message, 'error'); },
  warning(title, message = '') { return this.show(title, message, 'warning'); },
  info(title, message = '')    { return this.show(title, message, 'info'); },
};


// ── Modal System ──────────────────────────────────────────────────

const Modal = {
  open(backdropId) {
    const backdrop = document.getElementById(backdropId);
    if (!backdrop) return;
    backdrop.classList.add('active');
    document.body.style.overflow = 'hidden';
    backdrop.querySelector('.modal__close')?.focus();
  },

  close(backdropId) {
    const backdrop = document.getElementById(backdropId);
    if (!backdrop) return;
    backdrop.classList.remove('active');
    document.body.style.overflow = '';
  },

  closeAll() {
    document.querySelectorAll('.modal-backdrop.active').forEach(b => {
      b.classList.remove('active');
    });
    document.body.style.overflow = '';
  },

  init() {
    // Close modal on backdrop click
    document.querySelectorAll('.modal-backdrop').forEach(backdrop => {
      backdrop.addEventListener('click', e => {
        if (e.target === backdrop) this.closeAll();
      });
    });

    // Close on Escape
    document.addEventListener('keydown', e => {
      if (e.key === 'Escape') this.closeAll();
    });

    // Close buttons
    document.querySelectorAll('.modal__close, [data-modal-close]').forEach(btn => {
      btn.addEventListener('click', () => this.closeAll());
    });

    // Open buttons
    document.querySelectorAll('[data-modal-open]').forEach(btn => {
      btn.addEventListener('click', () => this.open(btn.dataset.modalOpen));
    });
  },
};


// ── Navbar ────────────────────────────────────────────────────────

const Navbar = {
  _initialized: false,

  init() {
    if (this._initialized) {
      this.renderAuthState();
      return;
    }
    this._initialized = true;

    const hamburger = document.getElementById('navbar-hamburger');
    const mobileMenu = document.getElementById('navbar-mobile');
    const navbar = document.getElementById('main-navbar');

    if (hamburger && mobileMenu) {
      // Toggle
      hamburger.addEventListener('click', () => {
        const isOpen = hamburger.classList.toggle('open');
        mobileMenu.classList.toggle('open', isOpen);
        hamburger.setAttribute('aria-expanded', isOpen.toString());
        hamburger.setAttribute('aria-label', isOpen ? 'Close menu' : 'Open menu');
      });

      // Close on mobile link click
      mobileMenu.querySelectorAll('.navbar__mobile-link').forEach(link => {
        link.addEventListener('click', () => {
          hamburger.classList.remove('open');
          mobileMenu.classList.remove('open');
        });
      });
    }

    // Scroll shadow
    if (navbar) {
      window.addEventListener('scroll', () => {
        navbar.classList.toggle('scrolled', window.scrollY > 20);
      }, { passive: true });
    }

    // Active link highlighting
    this.highlightActiveLink();
    this.cacheGuestState();
    this.renderAuthState();
    window.addEventListener('maitri:auth-change', event => this.renderAuthState(event.detail));
    window.addEventListener('maitri:language-change', () => {
      this.renderAuthState();
      HealthStatus.update();
    });
    this.restoreSession();
  },

  cacheGuestState() {
    document.querySelectorAll('.navbar__auth').forEach(area => {
      area.dataset.guestMarkup = area.innerHTML;
    });
  },

  storedUser() {
    try {
      if (typeof AuthSession !== 'undefined') {
        const u = AuthSession.user();
        if (u) return u;
      }
      const raw = sessionStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA) || localStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA);
      const u = JSON.parse(raw);
      if (u && u.role && typeof AuthSession !== 'undefined') {
        u.role = AuthSession.normalizeRole(u.role);
      }
      return u;
    } catch {
      return null;
    }
  },

  normalizeRole(role) {
    if (!role) return null;
    if (typeof AuthSession !== 'undefined' && typeof AuthSession.normalizeRole === 'function') {
      return AuthSession.normalizeRole(role);
    }
    const s = String(role).trim().toUpperCase();
    if (s === 'ROLE_VENDOR' || s === 'VENDOR') return 'VENDOR';
    if (s === 'ROLE_ADMIN' || s === 'ADMIN' || s === 'ROLE_SUPER_ADMIN' || s === 'SUPER_ADMIN') return 'ADMIN';
    if (s === 'ROLE_USER' || s === 'USER' || s === 'MEMBER') return 'USER';
    return s.replace(/^ROLE_/, '');
  },

  renderAuthState(user = this.storedUser()) {
    const authAreas = document.querySelectorAll('.navbar__auth');
    const mobileMenus = document.querySelectorAll('#navbar-mobile');

    // Show/hide auth-only nav links (e.g. Messages) based on login state
    this.toggleAuthLinks(!!user);
    this.toggleRoleNavLinks(user);

    if (!user) {
      authAreas.forEach(area => {
        if (area.dataset.guestMarkup) {
          area.innerHTML = area.dataset.guestMarkup;
          if (typeof I18n !== 'undefined') I18n.translatePage(area);
        }
      });
      mobileMenus.forEach(menu => {
        menu.querySelector('[data-auth-logout]')?.remove();
        menu.querySelector('[data-auth-profile]')?.remove();
      });
      return;
    }

    const role = this.normalizeRole(user.role);

    authAreas.forEach(area => {
      area.textContent = '';
      const greeting = document.createElement('span');
      greeting.style.cssText = 'font-size: var(--font-size-sm); color: var(--color-text-muted);';
      greeting.textContent = typeof I18n !== 'undefined'
        ? I18n.t('nav.greeting', { name: user.name || user.email })
        : `Hi, ${user.name || user.email}`;
      const profile = document.createElement('a');
      profile.href = this.profileHref(role);
      profile.className = role === 'VENDOR' ? 'btn btn--vendor btn--sm' : (role === 'ADMIN' ? 'btn btn--admin btn--sm' : 'btn btn--ghost btn--sm');
      profile.textContent = this.profileLabel(role);
      const logout = document.createElement('button');
      logout.type = 'button';
      logout.className = 'btn btn--outline btn--sm';
      logout.textContent = typeof I18n !== 'undefined' ? I18n.t('nav.logout') : 'Log Out';
      logout.addEventListener('click', () => this.logout());
      area.append(greeting, profile, logout);

      if (typeof Notifications !== 'undefined' && typeof Notifications.onNavbarRendered === 'function') {
        Notifications.onNavbarRendered();
      }
    });

    mobileMenus.forEach(menu => {
      let profile = menu.querySelector('[data-auth-profile]');
      let logout = menu.querySelector('[data-auth-logout]');

      if (!profile) {
        profile = document.createElement('a');
        profile.className = 'btn btn--outline btn--full';
        profile.dataset.authProfile = 'true';
        menu.append(profile);
      }
      profile.href = this.profileHref(role);
      profile.textContent = this.profileLabel(role);

      if (!logout) {
        logout = document.createElement('button');
        logout.type = 'button';
        logout.className = 'btn btn--outline btn--full';
        logout.dataset.authLogout = 'true';
        logout.addEventListener('click', () => this.logout());
        menu.append(logout);
      }
      logout.textContent = typeof I18n !== 'undefined' ? I18n.t('nav.logout') : 'Log Out';
    });
  },

  /** Role-aware relative href to the dashboard/profile page. */
  profileHref(role) {
    const isPages = window.location.pathname.includes('/pages/');
    const r = this.normalizeRole(role);
    if (r === 'VENDOR') {
      return isPages ? 'vendor-dashboard.html' : 'pages/vendor-dashboard.html';
    }
    if (r === 'ADMIN') {
      return isPages ? 'admin.html' : 'pages/admin.html';
    }
    return isPages ? 'user-profile.html' : 'pages/user-profile.html';
  },

  /** Role-aware label for the profile/dashboard button. */
  profileLabel(role) {
    const r = this.normalizeRole(role);
    if (r === 'VENDOR') {
      return typeof I18n !== 'undefined' ? (I18n.t('nav.vendorDashboard') || '🏪 Business Dashboard') : '🏪 Business Dashboard';
    }
    if (r === 'ADMIN') {
      return typeof I18n !== 'undefined' ? (I18n.t('nav.adminPanel') || '🛡️ Admin Panel') : '🛡️ Admin Panel';
    }
    return typeof I18n !== 'undefined' ? I18n.t('nav.myProfile') : '👤 My Profile';
  },

  /** Show or hide navigation links marked with data-auth-only. */
  toggleAuthLinks(show) {
    document.querySelectorAll('[data-auth-only]').forEach(link => {
      link.style.display = show ? '' : 'none';
    });
  },

  /** Conditionally show/hide navigation links based on user role. */
  toggleRoleNavLinks(user) {
    const role = this.normalizeRole(user ? user.role : (this.storedUser()?.role || null));
    const isVendor = role === 'VENDOR';
    const isAdmin = role === 'ADMIN';

    // Hide Browse Vendors link in main nav and mobile drawer for vendors and admins
    document.querySelectorAll('a[href*="vendors.html"]').forEach(link => {
      if (link.getAttribute('href') && link.getAttribute('href').includes('admin-vendors.html')) {
        return;
      }
      if (link.closest('.navbar__nav') || link.closest('#navbar-mobile') || link.classList.contains('navbar__link') || link.classList.contains('navbar__mobile-link')) {
        link.style.display = (isVendor || isAdmin) ? 'none' : '';
      }
    });
  },

  async restoreSession() {
    if (typeof AuthSession !== 'undefined') {
      await AuthSession.restore();
      return;
    }

    const token = sessionStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN) || localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
    if (!token) return;

    try {
      const result = await API.getCurrentUser();
      if (result.ok && result.data?.success && result.data.data) {
        sessionStorage.setItem(CONFIG.STORAGE_KEYS.USER_DATA, JSON.stringify(result.data.data));
        window.dispatchEvent(new CustomEvent('maitri:auth-change', { detail: result.data.data }));
      } else if (result.status === 401) {
        sessionStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
        sessionStorage.removeItem(CONFIG.STORAGE_KEYS.USER_DATA);
        localStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
        localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_DATA);
        window.dispatchEvent(new CustomEvent('maitri:auth-change', { detail: null }));
      }
    } catch {
      // Keep the local session during a temporary network outage.
    }
  },

  logout() {
    if (typeof AuthSession !== 'undefined') {
      AuthSession.logout();
      return;
    }

    sessionStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
    sessionStorage.removeItem(CONFIG.STORAGE_KEYS.USER_DATA);
    localStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
    localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_DATA);
    window.dispatchEvent(new CustomEvent('maitri:auth-change', { detail: null }));
    window.location.href = window.location.pathname.includes('/pages/') ? '../index.html' : 'index.html';
  },

  highlightActiveLink() {
    const current = window.location.pathname.split('/').pop() || 'index.html';
    document.querySelectorAll('.navbar__link, .navbar__mobile-link').forEach(link => {
      const href = link.getAttribute('href') || '';
      const page = href.split('/').pop();
      if (page === current || (current === '' && page === 'index.html')) {
        link.classList.add('active');
      }
    });
  },
};


// ── Backend Health Status ─────────────────────────────────────────

const HealthStatus = {
  _lastStatus: null,

  async init() {
    const dotEls    = document.querySelectorAll('.js-health-dot');
    const textEls   = document.querySelectorAll('.js-health-text');

    if (!dotEls.length) return;

    try {
      const result = await API.checkHealth();
      this._lastStatus = result.ok;
      this.render(result.ok);
    } catch {
      this._lastStatus = false;
      this.render(false);
    }
  },

  render(isOnline) {
    const dotEls  = document.querySelectorAll('.js-health-dot');
    const textEls = document.querySelectorAll('.js-health-text');
    dotEls.forEach(dot => {
      dot.className = 'status-dot status-dot--pulse ' +
        (isOnline ? 'status-dot--green' : 'status-dot--red');
    });
    textEls.forEach(el => {
      el.textContent = isOnline
        ? (typeof I18n !== 'undefined' ? I18n.t('footer.backendConnected') : 'Backend: Connected')
        : (typeof I18n !== 'undefined' ? I18n.t('footer.backendOffline') : 'Backend: Offline');
    });
  },

  update() {
    if (this._lastStatus !== null) {
      this.render(this._lastStatus);
    }
  }
};


// ── Smooth Scroll for anchor links ────────────────────────────────

function initSmoothScroll() {
  document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', e => {
      const target = document.querySelector(anchor.getAttribute('href'));
      if (target) {
        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  });
}


// ── Intersection Observer for fade-in animations ──────────────────

let scrollAnimationObserver = null;

function getScrollAnimationObserver() {
  if (!scrollAnimationObserver && typeof IntersectionObserver !== 'undefined') {
    scrollAnimationObserver = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          scrollAnimationObserver.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12 });
  }
  return scrollAnimationObserver;
}

/**
 * Observe elements with .animate-on-scroll for fade-in animation.
 * Can be called multiple times after dynamic content injection.
 * @param {HTMLElement|Document} root - container to query from (default: document)
 */
function observeScrollAnimations(root = document) {
  const observer = getScrollAnimationObserver();
  const scope = (root && typeof root.querySelectorAll === 'function') ? root : document;
  const elements = scope.querySelectorAll('.animate-on-scroll:not(.visible)');

  if (!observer) {
    // Fallback if IntersectionObserver is not supported
    elements.forEach(el => el.classList.add('visible'));
    return;
  }

  elements.forEach(el => observer.observe(el));
}

function initScrollAnimations() {
  observeScrollAnimations(document);
}

// ── HTML Sanitizer Helper ──────────────────────────────────────────

function escapeHtml(text) {
  if (text == null) return '';
  const div = document.createElement('div');
  div.textContent = String(text);
  return div.innerHTML;
}

// Expose globally for dynamic views
window.escapeHtml = escapeHtml;
window.observeScrollAnimations = observeScrollAnimations;
window.observeAnimatedElements = observeScrollAnimations;


// ── Global init ────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  Navbar.init();
  Modal.init();
  HealthStatus.init();
  initSmoothScroll();
  initScrollAnimations();
  if (typeof initPasswordToggles === 'function') initPasswordToggles();
});
