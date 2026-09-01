/**
 * Maitri — Admin Dashboard JavaScript
 *
 * Handles admin dashboard interactions:
 * - Auth state rendering
 * - Pending vendors fetch + UI
 * - User management
 * - Complaint moderation
 * Logout confirmation
 */

document.addEventListener('DOMContentLoaded', () => {
  // Check authentication state
  const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  if (!token) {
    // No token → redirect to admin login
    window.location.href = window.location.pathname.includes('/pages/') ? 'admin-login.html' : 'pages/admin-login.html';
    return;
  }

  // Render auth state in navbar
  API.getCurrentUser().then(result => {
    if (result.ok && result.data?.success && result.data.data) {
      const user = result.data.data;
      const greeting = document.getElementById('admin-user-greeting');
      if (greeting) {
        greeting.textContent = `Hello, ${user.name || 'Admin'}`;
      }

      // Block non-admins
      const role = typeof AuthSession !== 'undefined' ? AuthSession.normalizeRole(user.role) : String(user.role).replace(/^ROLE_/, '');
      if (role !== 'ADMIN') {
        const adminOnly = document.querySelectorAll('[data-admin-only]');
        adminOnly.forEach(el => el.style.display = 'none');
        if (typeof Toast !== 'undefined') {
          Toast.error('Access Denied', 'Administrator privileges required.');
        }
        setTimeout(() => {
          window.location.href = window.location.pathname.includes('/pages/') ? 'admin-login.html' : 'pages/admin-login.html';
        }, 1000);
      }
    }
  }).catch(() => {
    // Auth failed — clear tokens and redirect to admin login
    localStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
    localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_DATA);
    window.location.href = window.location.pathname.includes('/pages/') ? 'admin-login.html' : 'pages/admin-login.html';
  });

  // Initialize Pending Vendors section
  initPendingVendors();

  // Initialize User Management
  initUserManagement();

  // Initialize Logout
  initLogout();
});

/**
 * Initialize Pending Vendors section
 */
async function initPendingVendors() {
  try {
    const result = await API.getAdminPendingVendors();
    const list = document.getElementById('pending-vendors-list');
    const empty = document.getElementById('pending-vendors-empty');

    if (!result.ok || !result.data) {
      showToast('error', 'Failed to fetch pending vendors');
      if (list) list.style.display = 'none';
      if (empty) empty.style.display = 'block';
      return;
    }

    const vendors = result.data.data || [];
    const pendingVendors = vendors || [];

    if (pendingVendors.length === 0) {
      if (list) list.style.display = 'none';
      if (empty) empty.style.display = 'block';
      return;
    }

    if (list) list.style.display = 'block';
    if (empty) empty.style.display = 'none';

    const container = list.querySelector('.pending-vendors-container');
    if (!container) {
      // Build list HTML
      const html = pendingVendors.map(vendor => {
        const shopName = vendor.shopName || 'Unnamed Shop';
        const status = vendor.status || 'PENDING';
        return `
          <div class="pending-vendor-card">
            <div class="pending-vendor-card__info">
              <div class="pending-vendor-card__shop">${shopName}</div>
              <div class="pending-vendor-card__status status--pending">PENDING</div>
            </div>
            <div class="pending-vendor-card__actions">
              <button class="btn btn--sm btn--outline approve-vendor" data-vendor-id="${vendor.id}" aria-label="Approve vendor ${vendor.id}">
                Approve
              </button>
              <button class="btn btn--sm btn--reject reject-vendor" data-vendor-id="${vendor.id}" aria-label="Reject vendor ${vendor.id}">
                Reject
              </button>
            </div>
          </div>
        `;
      }).join('');

      container = document.createElement('div');
      container.className = 'pending-vendors-container';
      container.innerHTML = html;
      list.appendChild(container);
    }
  } catch (err) {
    showToast('error', 'Failed to load pending vendors');
  }
}

/**
 * Initialize User Management
 */
async function initUserManagement() {
  // Currently a placeholder — the user management page is at admin-users.html
  // This section of the dashboard provides quick access
  const stats = document.getElementById('stats-total-users');
  if (stats) {
    // Could fetch user count via API, but keeping it simple for now
    stats.textContent = '—';
  }
}

/**
 * Initialize Logout
 */
function initLogout() {
  const logoutBtn = document.getElementById('admin-user-logout');
  const modal = document.getElementById('logout-modal');
  const confirmBtn = document.getElementById('modal-logout-confirm');

  if (logoutBtn) {
    logoutBtn.addEventListener('click', (e) => {
      e.preventDefault();
      modal.classList.add('active');
    });
  }

  if (confirmBtn) {
    confirmBtn.addEventListener('click', () => {
      API.logout();
      window.location.href = window.location.pathname.includes('/pages/') ? '../index.html' : 'index.html';
    });
  }

  // Close modal on overlay click
  if (modal) {
    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        modal.classList.remove('active');
      }
    });
  }
}

/**
 * Show toast notification
 */
function showToast(type, message) {
  const Toast = window.Toast;
  if (Toast) {
    Toast.show(
      type === 'error' ? 'Error' : 'Success',
      message,
      type,
      5000
    );
  } else {
    // Fallton: simple alert
    alert(message);
  }
}