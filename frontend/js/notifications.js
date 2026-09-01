/**
 * Maitri — Notifications Module (Phase 10 & Enhancement).
 *
 * Handles the notification bell UI in the navbar:
 *   - Fetches unread count on load and shows an active counter badge
 *   - Opens an accessible dropdown panel with recent notifications
 *   - Allows marking individual notifications as read
 *   - Allows marking all notifications as read
 *   - On notification click: marks as read and navigates to the relevant page
 *   - Seamlessly hooks into Navbar.renderAuthState and page transitions
 *   - Handles 401/offline gracefully (never breaks navbar or layout)
 */
const Notifications = {
  // Cache & State
  _unreadCount: 0,
  _dropdownOpen: false,
  _pollTimer: null,
  _lastFetch: 0,

  // ─── Init ─────────────────────────────────────────────────────────────────
  init() {
    if (typeof API === 'undefined' || typeof CONFIG === 'undefined') {
      console.warn('[Notifications] API or CONFIG not loaded — skipping init');
      return;
    }

    const token = localStorage.getItem(CONFIG.STORAGE_KEYS?.AUTH_TOKEN || 'maitri_auth_token');
    if (!token) {
      return; // No bell for unauthenticated guests
    }

    this._createBell();
    this._fetchUnreadCount();
    this._startPolling();
    this._bindEvents();

    // Listen for auth state changes
    window.addEventListener('maitri:auth-change', event => {
      if (event.detail) {
        this._createBell();
        this._fetchUnreadCount();
        this._startPolling();
      } else {
        this._destroyBell();
        this._stopPolling();
      }
    });
  },

  /**
   * Called by Navbar whenever auth state renders to ensure bell is present.
   */
  onNavbarRendered() {
    const token = localStorage.getItem((typeof CONFIG !== 'undefined' && CONFIG.STORAGE_KEYS?.AUTH_TOKEN) || 'maitri_auth_token');
    if (token) {
      this._createBell();
      this._updateBadge(this._unreadCount);
    }
  },

  // ─── Bell Creation / Destruction ──────────────────────────────────────────

  _createBell() {
    const authAreas = document.querySelectorAll('.navbar__auth');
    const titleText = typeof I18n !== 'undefined' ? I18n.t('notifications.title') : 'Notifications';
    const markAllText = typeof I18n !== 'undefined' ? I18n.t('notifications.markAllRead') : 'Mark all read';
    const viewAllText = typeof I18n !== 'undefined' ? I18n.t('notifications.viewAll') : 'View all notifications';

    const isPages = window.location.pathname.includes('/pages/');
    const viewAllUrl = isPages ? 'user-profile.html#notifications' : 'pages/user-profile.html#notifications';

    authAreas.forEach(area => {
      if (area.querySelector('.navbar__bell-wrapper')) return;

      // Create bell button
      const bell = document.createElement('button');
      bell.type = 'button';
      bell.className = 'navbar__bell btn btn--ghost btn--icon';
      bell.setAttribute('aria-label', titleText);
      bell.setAttribute('aria-expanded', 'false');
      bell.setAttribute('aria-haspopup', 'true');
      bell.innerHTML = `
        <svg class="icon" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
          <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z" fill="currentColor"/>
        </svg>
        <span class="navbar__badge" aria-label="unread notifications" style="display:none;">0</span>
      `;

      // Create dropdown panel
      const dropdown = document.createElement('div');
      dropdown.className = 'navbar__dropdown';
      dropdown.setAttribute('role', 'menu');
      dropdown.innerHTML = `
        <div class="navbar__dropdown-header">
          <span>${this._escapeHtml(titleText)}</span>
          <button type="button" class="navbar__mark-all" aria-label="${markAllText}">${this._escapeHtml(markAllText)}</button>
        </div>
        <div class="navbar__dropdown-list" role="listbox"></div>
        <div class="navbar__dropdown-footer">
          <a href="${viewAllUrl}">${this._escapeHtml(viewAllText)}</a>
        </div>
      `;

      // Wrap bell + dropdown in a container for relative positioning
      const wrapper = document.createElement('div');
      wrapper.className = 'navbar__bell-wrapper';
      wrapper.appendChild(bell);
      wrapper.appendChild(dropdown);

      // Insert bell before logout / profile button
      const logoutBtn = area.querySelector('[data-auth-logout], button:last-of-type');
      if (logoutBtn) {
        area.insertBefore(wrapper, logoutBtn);
      } else {
        area.appendChild(wrapper);
      }

      // Bell click event
      bell.addEventListener('click', e => {
        e.stopPropagation();
        this._toggleDropdown(bell);
      });

      // Mark all read event
      dropdown.querySelector('.navbar__mark-all')?.addEventListener('click', e => {
        e.stopPropagation();
        this._markAllRead();
      });
    });
  },

  _destroyBell() {
    document.querySelectorAll('.navbar__bell-wrapper').forEach(w => w.remove());
    this._dropdownOpen = false;
  },

  // ─── Event Binding ────────────────────────────────────────────────────────

  _bindEvents() {
    // Close dropdown on outside click
    document.addEventListener('click', e => {
      if (this._dropdownOpen && !e.target.closest('.navbar__bell-wrapper')) {
        this._closeAllDropdowns();
      }
    });

    // Keyboard: Escape closes dropdown
    document.addEventListener('keydown', e => {
      if (e.key === 'Escape' && this._dropdownOpen) {
        this._closeAllDropdowns();
      }
    });
  },

  // ─── Dropdown Helpers ─────────────────────────────────────────────────────

  _toggleDropdown(bell) {
    const wrapper = bell.closest('.navbar__bell-wrapper');
    if (!wrapper) return;
    const dropdown = wrapper.querySelector('.navbar__dropdown');
    if (!dropdown) return;

    const isOpen = dropdown.classList.toggle('open');
    bell.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
    this._dropdownOpen = isOpen;

    if (isOpen) {
      this._renderDropdownList(dropdown);
    }
  },

  _closeAllDropdowns() {
    document.querySelectorAll('.navbar__dropdown').forEach(d => d.classList.remove('open'));
    document.querySelectorAll('.navbar__bell').forEach(b => b.setAttribute('aria-expanded', 'false'));
    this._dropdownOpen = false;
  },

  // ─── Fetch & Render ───────────────────────────────────────────────────────

  async _fetchUnreadCount() {
    try {
      if (typeof API !== 'undefined' && typeof API.getUnreadCount === 'function') {
        const result = await API.getUnreadCount();
        const res = (result && result.data && typeof result.data.success !== 'undefined') ? result.data : result;
        if (res && res.success && res.data) {
          this._updateBadge(res.data.count);
        }
      }
    } catch (err) {
      console.debug('[Notifications] Unread count fetch failed:', err.message);
    }
  },

  _updateBadge(count) {
    this._unreadCount = typeof count === 'number' ? count : 0;
    document.querySelectorAll('.navbar__badge').forEach(badge => {
      if (this._unreadCount > 0) {
        badge.textContent = this._unreadCount > 99 ? '99+' : this._unreadCount;
        badge.style.display = 'flex';
      } else {
        badge.style.display = 'none';
      }
    });
  },

  async _renderDropdownList(dropdown) {
    const list = dropdown.querySelector('.navbar__dropdown-list');
    if (!list) return;

    list.innerHTML = '<div class="navbar__dropdown-loading" style="padding:1rem; text-align:center; color:var(--color-text-muted);">Loading…</div>';

    try {
      const result = await API.getNotifications();
      const res = (result && result.data && typeof result.data.success !== 'undefined') ? result.data : result;
      if (!res || !res.success || !Array.isArray(res.data)) {
        list.innerHTML = '<div class="navbar__dropdown-empty" style="padding:1.5rem; text-align:center; color:var(--color-text-muted);">Failed to load notifications</div>';
        return;
      }

      const notifications = res.data;
      if (!notifications.length) {
        list.innerHTML = '<div class="navbar__dropdown-empty" style="padding:1.5rem; text-align:center; color:var(--color-text-muted);">No notifications yet</div>';
        return;
      }

      // Show max 10 in dropdown
      const toShow = notifications.slice(0, 10);
      list.innerHTML = toShow.map(n => this._notificationItemHtml(n)).join('');

      // Bind click handlers for mark-read and navigation
      list.querySelectorAll('.navbar__notification-item').forEach(item => {
        item.addEventListener('click', async e => {
          e.stopPropagation();
          const id = item.dataset.id;
          const targetUrl = item.dataset.targetUrl;

          if (!item.classList.contains('read')) {
            await this._markRead(id, item);
          }

          if (targetUrl) {
            window.location.href = targetUrl;
          }
        });
      });
    } catch (err) {
      list.innerHTML = '<div class="navbar__dropdown-empty" style="padding:1.5rem; text-align:center; color:var(--color-text-muted);">Failed to load notifications</div>';
      console.debug('[Notifications] Fetch failed:', err.message);
    }
  },

  _getTargetUrl(n) {
    const isPages = window.location.pathname.includes('/pages/');
    let role = null;
    try {
      const userKey = (typeof CONFIG !== 'undefined' && CONFIG.STORAGE_KEYS?.USER_DATA) || 'maitri_user_data';
      const stored = JSON.parse(localStorage.getItem(userKey));
      role = stored?.role;
    } catch {
      // ignore
    }
    const normRole = (role || '').toUpperCase().replace(/^ROLE_/, '');

    if (n.type === 'CHAT') {
      return isPages ? 'chat.html' : 'pages/chat.html';
    }
    if (n.type === 'VERIFICATION') {
      if (normRole === 'ADMIN') {
        return isPages ? 'admin-vendors.html' : 'pages/admin-vendors.html';
      }
      return isPages ? 'vendor-dashboard.html' : 'pages/vendor-dashboard.html';
    }
    if (n.type === 'COMPLAINT') {
      if (normRole === 'ADMIN') {
        return isPages ? 'admin.html' : 'pages/admin.html';
      }
      if (normRole === 'VENDOR') {
        return isPages ? 'vendor-dashboard.html#complaints' : 'pages/vendor-dashboard.html#complaints';
      }
      return isPages ? 'user-profile.html#complaints' : 'pages/user-profile.html#complaints';
    }
    if (n.type === 'REVIEW') {
      return isPages ? 'vendor-dashboard.html#reviews' : 'pages/vendor-dashboard.html#reviews';
    }
    return isPages ? 'user-profile.html#notifications' : 'pages/user-profile.html#notifications';
  },

  _notificationItemHtml(n) {
    const time = new Date(n.createdAt).toLocaleString(undefined, {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
    });
    const unreadClass = n.read ? 'read' : 'unread';
    const targetUrl = this._getTargetUrl(n);

    return `
      <div class="navbar__notification-item ${unreadClass}" data-id="${n.id}" data-target-url="${targetUrl}" role="option" style="cursor: pointer;">
        <div class="navbar__notification-icon" aria-hidden="true">
          ${this._iconForType(n.type)}
        </div>
        <div class="navbar__notification-content">
          <div class="navbar__notification-title" style="font-weight: 600; font-size: 0.85rem; color: var(--color-text-primary);">${this._escapeHtml(n.title)}</div>
          <div class="navbar__notification-message" style="font-size: 0.8rem; color: var(--color-text-secondary); margin-top: 2px;">${this._escapeHtml(n.message)}</div>
          <div class="navbar__notification-time" style="font-size: 0.72rem; color: var(--color-text-muted); margin-top: 4px;">${time}</div>
        </div>
        ${!n.read ? '<span class="navbar__notification-dot" aria-hidden="true"></span>' : ''}
      </div>
    `;
  },

  _iconForType(type) {
    switch (type) {
      case 'CHAT':
        return '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>';
      case 'VERIFICATION':
        return '<svg viewBox="0 0 24 24" width="18" height="18"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z" fill="currentColor"/></svg>';
      case 'COMPLAINT':
        return '<svg viewBox="0 0 24 24" width="18" height="18"><path d="M15.73 3H8.27L3 8.27v7.46L8.27 21h7.46L21 15.73V8.27L15.73 3zM12 17.3c-.72 0-1.3-.58-1.3-1.3 0-.72.58-1.3 1.3-1.3.72 0 1.3.58 1.3 1.3 0 .72-.58 1.3-1.3 1.3zm1-4.3h-2V7h2v6z" fill="currentColor"/></svg>';
      case 'REVIEW':
        return '<svg viewBox="0 0 24 24" width="18" height="18"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" fill="currentColor"/></svg>';
      default:
        return '<svg viewBox="0 0 24 24" width="18" height="18"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" fill="currentColor"/></svg>';
    }
  },

  _escapeHtml(str) {
    if (str == null) return '';
    const div = document.createElement('div');
    div.textContent = String(str);
    return div.innerHTML;
  },

  // ─── Mark Read / Mark All Read ────────────────────────────────────────────

  async _markRead(id, itemEl) {
    try {
      const result = await API.markNotificationRead(id);
      const res = (result && result.data && typeof result.data.success !== 'undefined') ? result.data : result;
      if (res && res.success) {
        itemEl.classList.remove('unread');
        itemEl.classList.add('read');
        itemEl.querySelector('.navbar__notification-dot')?.remove();
        this._fetchUnreadCount();
      }
    } catch (err) {
      console.debug('[Notifications] Mark read failed:', err.message);
    }
  },

  async _markAllRead() {
    try {
      const result = await API.markAllNotificationsRead();
      const res = (result && result.data && typeof result.data.success !== 'undefined') ? result.data : result;
      if (res && res.success) {
        this._fetchUnreadCount();
        this._closeAllDropdowns();
      }
    } catch (err) {
      console.debug('[Notifications] Mark all read failed:', err.message);
    }
  },

  // ─── Polling ──────────────────────────────────────────────────────────────

  _startPolling() {
    this._stopPolling();
    this._pollTimer = setInterval(() => this._fetchUnreadCount(), 30000);
  },

  _stopPolling() {
    if (this._pollTimer) {
      clearInterval(this._pollTimer);
      this._pollTimer = null;
    }
  },
};

// Global export
window.Notifications = Notifications;

// Auto-init on DOM ready
document.addEventListener('DOMContentLoaded', () => Notifications.init());