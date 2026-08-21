/**
 * Maitri — Notifications Module (Phase 10).
 *
 * Handles the notification bell UI in the navbar:
 *   - Fetches unread count on load and shows a badge
 *   - Opens a dropdown panel with recent notifications
 *   - Allows marking individual notifications as read
 *   - Allows marking all as read
 *   - Handles 401/offline gracefully (no broken navbar)
 */
const Notifications = {
  // Cache
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

    // Check if user is authenticated (token exists)
    const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
    if (!token) {
      return; // No bell for unauthenticated users
    }

    this._createBell();
    this._fetchUnreadCount();
    this._startPolling();
    this._bindEvents();

    // Listen for auth state changes
    window.addEventListener('maitri:auth-change', event => {
      if (event.detail) {
        // User logged in
        this._createBell();
        this._fetchUnreadCount();
        this._startPolling();
      } else {
        // User logged out
        this._destroyBell();
        this._stopPolling();
      }
    });
  },

  // ─── Bell Creation / Destruction ──────────────────────────────────────────

  _createBell() {
    const authAreas = document.querySelectorAll('.navbar__auth');
    authAreas.forEach(area => {
      // Check if bell already exists
      if (area.querySelector('.navbar__bell')) return;

      // Create bell button
      const bell = document.createElement('button');
      bell.type = 'button';
      bell.className = 'navbar__bell btn btn--ghost btn--icon';
      bell.setAttribute('aria-label', 'Notifications');
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
          <span>Notifications</span>
          <button type="button" class="navbar__mark-all" aria-label="Mark all as read">Mark all read</button>
        </div>
        <div class="navbar__dropdown-list" role="listbox"></div>
        <div class="navbar__dropdown-footer">
          <a href="pages/user-profile.html#notifications">View all notifications</a>
        </div>
      `;

      // Wrap bell + dropdown in a container for positioning
      const wrapper = document.createElement('div');
      wrapper.className = 'navbar__bell-wrapper';
      wrapper.appendChild(bell);
      wrapper.appendChild(dropdown);

      // Insert bell before the profile button (last child before logout)
      const logoutBtn = area.querySelector('[data-auth-logout], button:last-of-type');
      if (logoutBtn) {
        area.insertBefore(wrapper, logoutBtn);
      } else {
        area.appendChild(wrapper);
      }
    });
  },

  _destroyBell() {
    document.querySelectorAll('.navbar__bell-wrapper').forEach(w => w.remove());
    this._dropdownOpen = false;
  },

  // ─── Event Binding ────────────────────────────────────────────────────────

  _bindEvents() {
    // Bell click → toggle dropdown
    document.querySelectorAll('.navbar__bell').forEach(bell => {
      bell.addEventListener('click', e => {
        e.stopPropagation();
        this._toggleDropdown(bell);
      });
    });

    // Mark all read button
    document.querySelectorAll('.navbar__mark-all').forEach(btn => {
      btn.addEventListener('click', e => {
        e.stopPropagation();
        this._markAllRead();
      });
    });

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
    const dropdown = wrapper.querySelector('.navbar__dropdown');
    const isOpen = dropdown.classList.toggle('open');
    bell.setAttribute('aria-expanded', isOpen);
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
      const result = await API.getUnreadCount();
      if (result.success && result.data) {
        this._updateBadge(result.data.count);
      }
    } catch (err) {
      // Silently fail — don't break the navbar
      console.debug('[Notifications] Unread count fetch failed:', err.message);
    }
  },

  _updateBadge(count) {
    this._unreadCount = count;
    document.querySelectorAll('.navbar__badge').forEach(badge => {
      if (count > 0) {
        badge.textContent = count > 99 ? '99+' : count;
        badge.style.display = 'flex';
      } else {
        badge.style.display = 'none';
      }
    });
  },

  async _renderDropdownList(dropdown) {
    const list = dropdown.querySelector('.navbar__dropdown-list');
    list.innerHTML = '<div class="navbar__dropdown-loading">Loading…</div>';

    try {
      const result = await API.getNotifications();
      if (!result.success || !result.data) {
        list.innerHTML = '<div class="navbar__dropdown-empty">Failed to load notifications</div>';
        return;
      }

      const notifications = result.data;
      if (!notifications.length) {
        list.innerHTML = '<div class="navbar__dropdown-empty">No notifications yet</div>';
        return;
      }

      // Show max 10 in dropdown
      const toShow = notifications.slice(0, 10);
      list.innerHTML = toShow.map(n => this._notificationItemHtml(n)).join('');

      // Bind click handlers for mark-read
      list.querySelectorAll('.navbar__notification-item').forEach(item => {
        item.addEventListener('click', e => {
          e.stopPropagation();
          const id = item.dataset.id;
          if (!item.classList.contains('read')) {
            this._markRead(id, item);
          }
        });
      });
    } catch (err) {
      list.innerHTML = '<div class="navbar__dropdown-empty">Failed to load notifications</div>';
      console.debug('[Notifications] Fetch failed:', err.message);
    }
  },

  _notificationItemHtml(n) {
    const time = new Date(n.createdAt).toLocaleString(undefined, {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
    });
    const unreadClass = n.read ? 'read' : 'unread';
    return `
      <div class="navbar__notification-item ${unreadClass}" data-id="${n.id}" role="option">
        <div class="navbar__notification-icon" aria-hidden="true">
          ${this._iconForType(n.type)}
        </div>
        <div class="navbar__notification-content">
          <div class="navbar__notification-title">${this._escapeHtml(n.title)}</div>
          <div class="navbar__notification-message">${this._escapeHtml(n.message)}</div>
          <div class="navbar__notification-time">${time}</div>
        </div>
        ${!n.read ? '<span class="navbar__notification-dot" aria-hidden="true"></span>' : ''}
      </div>
    `;
  },

  _iconForType(type) {
    switch (type) {
      case 'VERIFICATION': return '<svg viewBox="0 0 24 24" width="18" height="18"><path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z" fill="currentColor"/></svg>';
      case 'COMPLAINT': return '<svg viewBox="0 0 24 24" width="18" height="18"><path d="M15.73 3H8.27L3 8.27v7.46L8.27 21h7.46L21 15.73V8.27L15.73 3zM12 17.3c-.72 0-1.3-.58-1.3-1.3 0-.72.58-1.3 1.3-1.3.72 0 1.3.58 1.3 1.3 0 .72-.58 1.3-1.3 1.3zm1-4.3h-2V7h2v6z" fill="currentColor"/></svg>';
      case 'REVIEW': return '<svg viewBox="0 0 24 24" width="18" height="18"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" fill="currentColor"/></svg>';
      default: return '<svg viewBox="0 0 24 24" width="18" height="18"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" fill="currentColor"/></svg>';
    }
  },

  _escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  },

  // ─── Mark Read / Mark All Read ────────────────────────────────────────────

  async _markRead(id, itemEl) {
    try {
      const result = await API.markNotificationRead(id);
      if (result.success) {
        itemEl.classList.remove('unread');
        itemEl.classList.add('read');
        itemEl.querySelector('.navbar__notification-dot')?.remove();
        this._fetchUnreadCount(); // refresh badge
      }
    } catch (err) {
      console.debug('[Notifications] Mark read failed:', err.message);
    }
  },

  async _markAllRead() {
    try {
      const result = await API.markAllNotificationsRead();
      if (result.success) {
        this._fetchUnreadCount();
        // Close dropdown and re-render if open
        this._closeAllDropdowns();
      }
    } catch (err) {
      console.debug('[Notifications] Mark all read failed:', err.message);
    }
  },

  // ─── Polling ──────────────────────────────────────────────────────────────

  _startPolling() {
    this._stopPolling();
    // Poll every 30 seconds for unread count
    this._pollTimer = setInterval(() => this._fetchUnreadCount(), 30000);
  },

  _stopPolling() {
    if (this._pollTimer) {
      clearInterval(this._pollTimer);
      this._pollTimer = null;
    }
  },
};

// Auto-init on DOM ready
document.addEventListener('DOMContentLoaded', () => Notifications.init());