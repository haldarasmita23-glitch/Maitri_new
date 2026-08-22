/**
 * Maitri — Admin Vendors JavaScript
 *
 * Handles vendor management dashboard functionality:
 * - Fetch and display pending vendors
 * - Approve/reject vendors
 */
document.addEventListener('DOMContentLoaded', () => {
  const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  if (!token) {
    window.location.href = 'index.html';
    return;
  }

  // Render auth state
  API.getCurrentUser().then(result => {
    if (result.ok && result.data?.success && result.data.data) {
      const user = result.data.data;
      const greeting = document.getElementById('admin-user-greeting');
      if (greeting) {
        greeting.textContent = `Hello, ${user.role !== 'ADMIN' && user.role !== 'SUPER_ADMIN' ? 'User' : 'Admin'}`;
      }
      if (user.role !== 'ADMIN' && user.role !== 'SUPER_ADMIN') {
        setTimeout(() => window.location.href = 'index.html', 2000);
      }
    }
  });

  // Initialize pending vendors
  initPendingVendors();

  // Initialize logout
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
        const id = vendor.id || 'unknown';
        return `
          <div class="pending-vendor-card">
            <div class="pending-vendor-card__info">
              <div class="pending-vendor-card__shop">${shopName}</div>
              <div class="pending-vendor-card__status status--pending">PENDING</div>
            </div>
            <div class="pending-vendor-card__actions">
              <button class="btn btn--sm btn--outline approve-vendor" data-vendor-id="${id}" aria-label="Approve vendor ${id}">
                Approve
              </button>
              <button class="btn btn--sm btn--reject reject-vendor" data-vendor-id="${id}" aria-label="Reject vendor ${id}">
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

    // Add event listeners for approve/reject buttons
    document.querySelectorAll('.approve-vendor').forEach(btn => {
      btn.addEventListener('click', () => {
        const vendorId = btn.getAttribute('data-vendor-id');
        if (vendorId && confirm(`Approve vendor ID ${vendorId}?`)) {
          API.approveVendor(vendorId).then(result => {
            if (result.ok && result.data?.success) {
              showToast('success', 'Vendor approved successfully');
              // Refresh the list
              initPendingVendors();
            } else {
              showToast('error', result.data?.message || 'Failed to approve vendor');
            }
          }).catch(err => {
            showToast('error', 'Failed to approve vendor');
          });
        }
      });
    });

    document.querySelectorAll('.reject-vendor').forEach(btn => {
      btn.addEventListener('click', () => {
        const vendorId = btn.getAttribute('data-vendor-id');
        if (vendorId && confirm(`Reject vendor ID ${vendorId}?`)) {
          API.rejectVendor(vendorId).then(result => {
            if (result.ok && result.data?.success) {
              showToast('success', 'Vendor rejected successfully');
              // Refresh the list
              initPendingVendors();
            } else {
              showToast('error', result.data?.message || 'Failed to reject vendor');
            }
          }).catch(err => {
            showToast('error', 'Failed to reject vendor');
          });
        }
      });
    });
  } catch (err) {
    showToast('error', 'Failed to load pending vendors');
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
    alert(message);
  }
}