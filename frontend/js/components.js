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
  init() {
    const hamburger = document.getElementById('navbar-hamburger');
    const mobileMenu = document.getElementById('navbar-mobile');
    const navbar = document.getElementById('main-navbar');

    if (!hamburger || !mobileMenu) return;

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
    this.restoreSession();
  },

  cacheGuestState() {
    document.querySelectorAll('.navbar__auth').forEach(area => {
      area.dataset.guestMarkup = area.innerHTML;
    });
  },

  storedUser() {
    try {
      return JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA));
    } catch {
      return null;
    }
  },

  renderAuthState(user = this.storedUser()) {
    const authAreas = document.querySelectorAll('.navbar__auth');
    const mobileMenus = document.querySelectorAll('#navbar-mobile');
    if (!user) {
      authAreas.forEach(area => {
        if (area.dataset.guestMarkup) area.innerHTML = area.dataset.guestMarkup;
      });
      mobileMenus.forEach(menu => {
        menu.querySelector('[data-auth-logout]')?.remove();
        menu.querySelector('[data-auth-profile]')?.remove();
      });
      return;
    }

    authAreas.forEach(area => {
      area.textContent = '';
      const greeting = document.createElement('span');
      greeting.style.cssText = 'font-size: var(--font-size-sm); color: var(--color-text-muted);';
      greeting.textContent = `Hi, ${user.name || user.email}`;
      const profile = document.createElement('a');
      profile.href = this.profileHref();
      profile.className = 'btn btn--ghost btn--sm';
      profile.textContent = '👤 My Profile';
      const logout = document.createElement('button');
      logout.type = 'button';
      logout.className = 'btn btn--outline btn--sm';
      logout.textContent = 'Log Out';
      logout.addEventListener('click', () => this.logout());
      area.append(greeting, profile, logout);
    });

    mobileMenus.forEach(menu => {
      const existing = menu.querySelector('[data-auth-logout]');
      if (existing) return;
      const profile = document.createElement('a');
      profile.href = this.profileHref();
      profile.className = 'btn btn--outline btn--full';
      profile.dataset.authProfile = 'true';
      profile.textContent = '👤 My Profile';
      const logout = document.createElement('button');
      logout.type = 'button';
      logout.className = 'btn btn--outline btn--full';
      logout.dataset.authLogout = 'true';
      logout.textContent = 'Log Out';
      logout.addEventListener('click', () => this.logout());
      menu.append(profile, logout);
    });
  },

  /** Relative href to the user profile page, valid from both /pages/ and the root. */
  profileHref() {
    return window.location.pathname.includes('/pages/') ? 'user-profile.html' : 'pages/user-profile.html';
  },

  async restoreSession() {
    if (typeof AuthSession !== 'undefined') {
      await AuthSession.restore();
      return;
    }

    const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
    if (!token) return;

    try {
      const result = await API.getCurrentUser();
      if (result.ok && result.data?.success && result.data.data) {
        localStorage.setItem(CONFIG.STORAGE_KEYS.USER_DATA, JSON.stringify(result.data.data));
        window.dispatchEvent(new CustomEvent('maitri:auth-change', { detail: result.data.data }));
      } else if (result.status === 401) {
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
  async init() {
    const dotEls    = document.querySelectorAll('.js-health-dot');
    const textEls   = document.querySelectorAll('.js-health-text');

    if (!dotEls.length) return;

    try {
      const result = await API.checkHealth();
      dotEls.forEach(dot => {
        dot.className = 'status-dot status-dot--pulse ' +
          (result.ok ? 'status-dot--green' : 'status-dot--red');
      });
      textEls.forEach(el => {
        el.textContent = result.ok ? 'Backend: Connected' : 'Backend: Offline';
      });
    } catch {
      dotEls.forEach(dot => {
        dot.className = 'status-dot status-dot--red';
      });
      textEls.forEach(el => { el.textContent = 'Backend: Offline'; });
    }
  },
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

function initScrollAnimations() {
  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12 });

  document.querySelectorAll('.animate-on-scroll').forEach(el => observer.observe(el));
}


// ── Global init ────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  Navbar.init();
  Modal.init();
  HealthStatus.init();
  initSmoothScroll();
  initScrollAnimations();
});
