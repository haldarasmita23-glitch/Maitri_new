/**
 * Maitri — Admin Vendors JavaScript
 *
 * Handles vendor management dashboard functionality:
 * - Fetch and display pending vendors
 * - Approve/reject vendors with live feedback
 */
document.addEventListener('DOMContentLoaded', () => {
  const token = sessionStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN) || localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  if (!token) {
    window.location.href = 'admin-login.html';
    return;
  }

  // Render auth state
  API.getCurrentUser().then(result => {
    if (result.ok && result.data?.success && result.data.data) {
      const user = result.data.data;
      const greeting = document.getElementById('admin-user-greeting');
      if (greeting) {
        greeting.textContent = `Hello, ${user.name || 'Admin'}`;
      }
      const role = typeof AuthSession !== 'undefined' ? AuthSession.normalizeRole(user.role) : String(user.role).replace(/^ROLE_/, '');
      if (role !== 'ADMIN') {
        if (typeof Toast !== 'undefined') {
          Toast.error('Access Denied', 'Administrator privileges required.');
        }
        setTimeout(() => { window.location.href = 'admin-login.html'; }, 1000);
      }
    } else {
      window.location.href = 'admin-login.html';
    }
  }).catch(() => {
    window.location.href = 'admin-login.html';
  });

  // Initialize pending vendors
  initPendingVendors();

  // Initialize logout
  initLogout();
});

/**
 * Local HTML escape helper (with window fallback)
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
  const empty = document.getElementById('pending-vendors-empty');

  try {
    const result = await API.getAdminPendingVendors();

    if (!result.ok || !result.data || !result.data.success) {
      showToast('error', result.data?.message || 'Failed to fetch pending vendors');
      if (list) {
        list.querySelectorAll('.pending-vendor-card').forEach(el => el.remove());
      }
      if (empty) empty.style.display = 'block';
      return;
    }

    const vendors = result.data.data || [];
    const pendingVendors = Array.isArray(vendors) ? vendors : [];

    // Clear previously rendered vendor cards
    if (list) {
      list.querySelectorAll('.pending-vendor-card').forEach(el => el.remove());
    }

    if (pendingVendors.length === 0) {
      if (empty) empty.style.display = 'block';
      return;
    }

    if (empty) empty.style.display = 'none';

    // Render cards into grid container
    pendingVendors.forEach(vendor => {
      const shopName = vendor.shopName || 'Unnamed Shop';
      const ownerName = vendor.ownerName || 'Unknown Owner';
      const categoryName = vendor.categoryName || vendor.categorySlug || 'General';
      const area = vendor.area || '';
      const phone = vendor.phone || '';
      const address = vendor.address || '';
      const id = vendor.id || '';

      const card = document.createElement('div');
      card.className = 'pending-vendor-card';
      card.id = `vendor-card-${id}`;
      card.innerHTML = `
        <div class="pending-vendor-card__info">
          <div class="pending-vendor-card__shop">${escapeText(shopName)}</div>
          <div class="pending-vendor-card__owner">👤 ${escapeText(ownerName)}</div>
          <div class="pending-vendor-card__meta">
            <span>🏷️ ${escapeText(categoryName)}</span>
            ${area ? `<span>📍 ${escapeText(area)}</span>` : ''}
            ${phone ? `<span>📞 ${escapeText(phone)}</span>` : ''}
          </div>
          ${address ? `<p style="font-size: var(--font-size-xs); color: var(--color-text-muted); margin: var(--space-2) 0 0;">${escapeText(address)}</p>` : ''}
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
      list.appendChild(card);
    });

    // Add event listeners for approve buttons
    list.querySelectorAll('.approve-vendor').forEach(btn => {
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

    // Add event listeners for reject buttons
    list.querySelectorAll('.reject-vendor').forEach(btn => {
      btn.addEventListener('click', async () => {
        const vendorId = btn.getAttribute('data-vendor-id');
        const shop = btn.getAttribute('data-shop-name') || 'Vendor';
        if (!vendorId) return;

        if (!confirm(`Are you sure you want to reject the application for "${shop}"?`)) {
          return;
        }

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
    showToast('error', 'Failed to load pending vendors');
    if (empty) empty.style.display = 'block';
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