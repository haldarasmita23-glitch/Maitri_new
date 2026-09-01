/**
 * Maitri — Admin Dashboard JavaScript
 *
 * Handles admin dashboard interactions:
 * - Auth state rendering
 * - Pending vendors fetch + KPI stats + preview UI
 * - User management stats
 * - Complaint moderation stats
 * - Logout confirmation
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

  // Initialize Pending Vendors section & KPI
  initPendingVendors();

  // Initialize User Management stats
  initUserManagement();

  // Initialize Complaints stats
  initComplaintStats();

  // Initialize Logout
  initLogout();
});

/**
 * Local HTML escape helper
 */
function escapeText(text) {
  if (typeof escapeHtml === 'function') return escapeHtml(text);
  if (text == null) return '';
  const div = document.createElement('div');
  div.textContent = String(text);
  return div.innerHTML;
}

/**
 * Initialize Pending Vendors section
 */
async function initPendingVendors() {
  const list = document.getElementById('pending-vendors-list');
  const statsVal = document.getElementById('stats-pending-vendors');

  try {
    const result = await API.getAdminPendingVendors();

    if (!result.ok || !result.data || !result.data.success) {
      if (statsVal) statsVal.textContent = '0';
      if (list) {
        list.innerHTML = `
          <div class="empty-state" style="padding: var(--space-6) 0;">
            <p style="color: var(--color-text-muted);">Failed to load pending vendors</p>
          </div>
        `;
      }
      return;
    }

    const vendors = result.data.data || [];
    const pendingVendors = Array.isArray(vendors) ? vendors : [];

    if (statsVal) {
      statsVal.textContent = String(pendingVendors.length);
    }

    if (!list) return;

    if (pendingVendors.length === 0) {
      list.innerHTML = `
        <div class="empty-state" style="padding: var(--space-6) 0;">
          <p style="color: var(--color-text-muted);" data-i18n="admin.noPendingVendors">No pending vendors awaiting review</p>
        </div>
      `;
      return;
    }

    // Build list preview HTML
    list.innerHTML = '';
    const container = document.createElement('div');
    container.className = 'pending-vendors-container';

    pendingVendors.forEach(vendor => {
      const shopName = vendor.shopName || 'Unnamed Shop';
      const ownerName = vendor.ownerName || 'Unknown Owner';
      const categoryName = vendor.categoryName || vendor.categorySlug || 'General';
      const area = vendor.area || '';
      const phone = vendor.phone || '';
      const id = vendor.id || '';

      const card = document.createElement('div');
      card.className = 'pending-vendor-card';
      card.innerHTML = `
        <div class="pending-vendor-card__info">
          <div class="pending-vendor-card__shop">${escapeText(shopName)}</div>
          <div class="pending-vendor-card__owner">👤 ${escapeText(ownerName)}</div>
          <div class="pending-vendor-card__meta">
            <span>🏷️ ${escapeText(categoryName)}</span>
            ${area ? `<span>📍 ${escapeText(area)}</span>` : ''}
            ${phone ? `<span>📞 ${escapeText(phone)}</span>` : ''}
          </div>
        </div>
        <div class="pending-vendor-card__actions">
          <button class="btn btn--sm btn--primary approve-vendor" data-vendor-id="${id}" data-shop-name="${escapeText(shopName)}" aria-label="Approve vendor ${escapeText(shopName)}">
            ✓ Approve
          </button>
          <button class="btn btn--sm btn--outline reject-vendor" data-vendor-id="${id}" data-shop-name="${escapeText(shopName)}" aria-label="Reject vendor ${escapeText(shopName)}" style="color: var(--color-error); border-color: var(--color-error);">
            ✕ Reject
          </button>
        </div>
      `;
      container.appendChild(card);
    });

    list.appendChild(container);

    // Attach approve handlers
    container.querySelectorAll('.approve-vendor').forEach(btn => {
      btn.addEventListener('click', async () => {
        const vendorId = btn.getAttribute('data-vendor-id');
        const shop = btn.getAttribute('data-shop-name') || 'Vendor';
        if (!vendorId) return;

        btn.disabled = true;
        btn.textContent = 'Approving…';

        try {
          const res = await API.approveVendor(vendorId);
          if (res.ok && res.data?.success) {
            showToast('success', `${shop} approved successfully.`);
            await initPendingVendors();
          } else {
            showToast('error', res.data?.message || 'Failed to approve vendor.');
            btn.disabled = false;
            btn.textContent = '✓ Approve';
          }
        } catch {
          showToast('error', 'Failed to approve vendor.');
          btn.disabled = false;
          btn.textContent = '✓ Approve';
        }
      });
    });

    // Attach reject handlers
    container.querySelectorAll('.reject-vendor').forEach(btn => {
      btn.addEventListener('click', async () => {
        const vendorId = btn.getAttribute('data-vendor-id');
        const shop = btn.getAttribute('data-shop-name') || 'Vendor';
        if (!vendorId) return;

        if (!confirm(`Are you sure you want to reject "${shop}"?`)) return;

        btn.disabled = true;
        btn.textContent = 'Rejecting…';

        try {
          const res = await API.rejectVendor(vendorId);
          if (res.ok && res.data?.success) {
            showToast('info', `${shop} application rejected.`);
            await initPendingVendors();
          } else {
            showToast('error', res.data?.message || 'Failed to reject vendor.');
            btn.disabled = false;
            btn.textContent = '✕ Reject';
          }
        } catch {
          showToast('error', 'Failed to reject vendor.');
          btn.disabled = false;
          btn.textContent = '✕ Reject';
        }
      });
    });

  } catch (err) {
    if (statsVal) statsVal.textContent = '0';
    if (list) {
      list.innerHTML = `
        <div class="empty-state" style="padding: var(--space-6) 0;">
          <p style="color: var(--color-text-muted);">Failed to load pending vendors</p>
        </div>
      `;
    }
  }
}

/**
 * Initialize User Management stats
 */
async function initUserManagement() {
  const stats = document.getElementById('stats-total-users');
  if (!stats) return;

  try {
    const res = await API.request('/admin/users', { auth: true });
    if (res.ok && res.data?.data && Array.isArray(res.data.data)) {
      stats.textContent = String(res.data.data.length);
    } else {
      stats.textContent = '—';
    }
  } catch {
    stats.textContent = '—';
  }
}

/**
 * Initialize Complaint stats
 */
async function initComplaintStats() {
  const stats = document.getElementById('stats-active-complaints');
  if (!stats) return;

  try {
    const res = await API.getAdminComplaints();
    if (res.ok && res.data?.data && Array.isArray(res.data.data)) {
      const activeCount = res.data.data.filter(c => c.status === 'PENDING' || c.status === 'IN_PROGRESS').length;
      stats.textContent = String(activeCount);
    } else {
      stats.textContent = '0';
    }
  } catch {
    stats.textContent = '0';
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
      if (modal) {
        modal.classList.add('active');
      } else {
        if (typeof AuthSession !== 'undefined') {
          AuthSession.logout();
        } else {
          localStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
          localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_DATA);
          window.location.href = 'admin-login.html';
        }
      }
    });
  }

  if (confirmBtn) {
    confirmBtn.addEventListener('click', () => {
      if (typeof AuthSession !== 'undefined') {
        AuthSession.logout();
      } else {
        localStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
        localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_DATA);
        window.location.href = 'admin-login.html';
      }
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
      type === 'error' ? 'Error' : (type === 'info' ? 'Notice' : 'Success'),
      message,
      type,
      5000
    );
  } else {
    alert(message);
  }
}