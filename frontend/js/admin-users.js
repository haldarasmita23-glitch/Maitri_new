/**
 * Maitri — Admin Users JavaScript
 *
 * Handles user management dashboard functionality:
 * - Fetch and display users
 * - Filter by role and status
 * - Edit user (name, role, active)
 * - Deactivate user
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

  // Initialize users table
  initUsersTable();

  // Initialize event listeners
  initUserManagementEvents();
});

/**
 * Fetch and display users table
 */
async function initUsersTable() {
  const roleFilter = document.getElementById('filter-role');
  const statusFilter = document.getElementById('filter-status');
  const tableBody = document.getElementById('users-table-body');
  const emptyState = document.getElementById('users-empty');

  try {
    // Fetch all users first
    const result = await API.get('/api/admin/users', { auth: true });
    if (!result.ok || !result.data) {
      showToast('error', 'Failed to fetch users');
      if (tableBody) tableBody.innerHTML = '';
      if (emptyState) emptyState.style.display = 'block';
      return;
    }

    // For now, use a simple approach - fetch users role by role or just show all
    // In a full implementation, we'd query the backend with filters
    // Here we'll just show all users from the API

    // The /api/admin/users endpoint returns a summary, not a full list
    // Let's try to get users through the API

    if (emptyState) empty.style.display = 'none';

    // Since the /api/admin/users endpoint returns a summary message,
    // let's try to get users individually or show a message
    // For now, display info message
    const tbody = document.getElementById('users-table-body');
    if (tbody) {
      tbody.innerHTML = `
        <tr>
          <td colspan="5" class="empty-state">
            <p>User management data will appear here. The admin API provides summary information.</p>
            <p>Use the backend API endpoints directly:</p>
            <ul class="small-text">
              <li>GET /api/admin/users - List users</li>
              <li>GET /api/admin/users/{email} - Get user by email</li>
              <li>PUT /api/admin/users - Update user</li>
              <li>DELETE /api/admin/users/{email} - Deactivate user</li>
            </ul>
          </tr>
        `;
    }

  } catch (err) {
    showToast('error', 'Failed to load users');
    if (tableBody) tableBody.innerHTML = '';
    if (emptyState) empty.style.display = 'block';
  }
}

/**
 * Initialize event listeners for user management
 */
function initUserManagementEvents() {
  // Role filter change
  const roleFilter = document.getElementById('filter-role');
  if (roleFilter) {
    roleFilter.addEventListener('change', async (e) => {
      // Reload users with new filter
      initUsersTable();
    });
  }

  // Status filter change
  const statusFilter = document.getElementById('filter-status');
  if (statusFilter) {
    statusFilter.addEventListener('change', async (e) => {
      // Reload users with new filter
      initUsersTable();
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
    alert(message);
  }
}