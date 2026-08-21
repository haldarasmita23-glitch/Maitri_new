/**
 * Maitri — Complaints Module (Phase 9)
 *
 * Renders the authenticated user's complaint history ("My Complaints") and
 * provides the "Raise a Complaint" form on the vendor detail page.
 *
 * Auth guard: users without a session see a "log in required" panel instead
 * of the form. A 401 during load (expired token) clears the session.
 *
 * Reuses the existing Toast / Navbar / AuthSession / API patterns.
 */

const ComplaintStatusLabels = {
  PENDING: 'Pending',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
};

const ComplaintStatusBadge = {
  PENDING: 'badge--amber',
  IN_PROGRESS: 'badge--primary',
  RESOLVED: 'badge--green',
};

/** Returns the locally stored user (or null). Safe when components.js is absent. */
function getCurrentUser() {
  try {
    return (typeof Navbar !== 'undefined' && Navbar.storedUser())
      || JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA))
      || null;
  } catch {
    return null;
  }
}

/** Renders a status badge for a complaint. */
function complaintStatusBadge(status) {
  const label = ComplaintStatusLabels[status] || status || 'Unknown';
  const cls = ComplaintStatusBadge[status] || 'badge--gray';
  return `<span class="badge ${cls}">${label}</span>`;
}

/** Formats a date string for display. */
function formatComplaintDate(value) {
  if (!value) return '—';
  try {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
  } catch {
    return '—';
  }
}

/**
 * Renders the authenticated user's complaint history into a container.
 * @param {HTMLElement} container - The element to render into
 */
async function renderMyComplaints(container) {
  if (!container) return;

  const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  if (!token) {
    container.innerHTML = `
      <div class="alert alert--info" role="note">
        <span class="alert__icon">🔐</span>
        <div class="alert__text">
          <strong>Log in required</strong><br>
          Please <a href="login.html">log in</a> to view your complaints.
        </div>
      </div>
    `;
    return;
  }

  container.innerHTML = `<div class="spinner" aria-label="Loading complaints"></div>`;

  try {
    const result = await API.getMyComplaints();
    if (result.status === 401) {
      AuthSession.clear();
      container.innerHTML = `
        <div class="alert alert--info" role="note">
          <span class="alert__icon">🔐</span>
          <div class="alert__text">
            <strong>Session expired</strong><br>
            Please <a href="login.html">log in</a> again to view your complaints.
          </div>
        </div>
      `;
      return;
    }
    if (!result.ok || !result.data?.success) {
      container.innerHTML = `
        <div class="alert alert--error" role="alert">
          <span class="alert__icon">⚠️</span>
          <div class="alert__text">
            <strong>Could not load complaints</strong><br>
            ${result.data?.message || 'Please try again later.'}
          </div>
        </div>
      `;
      return;
    }

    const complaints = result.data.data || [];
    if (complaints.length === 0) {
      container.innerHTML = `
        <div class="empty-state">
          <div class="empty-state__icon">📋</div>
          <h3>No complaints yet</h3>
          <p>You haven't raised any complaints. Visit a vendor page to raise one.</p>
        </div>
      `;
      return;
    }

    container.innerHTML = complaints.map(c => `
      <div class="complaint-card">
        <div class="complaint-card__header">
          <div>
            <div class="complaint-card__type">${escapeHtml(c.complaintType || 'Complaint')}</div>
            <div class="complaint-card__vendor">${escapeHtml(c.vendorName || 'Unknown Vendor')}</div>
          </div>
          ${complaintStatusBadge(c.status)}
        </div>
        <p class="complaint-card__description">${escapeHtml(c.description || '')}</p>
        <div class="complaint-card__meta">
          <span>Raised ${formatComplaintDate(c.createdAt)}</span>
          <span>Updated ${formatComplaintDate(c.updatedAt)}</span>
        </div>
        ${c.status === 'PENDING' ? `
          <div class="complaint-card__actions">
            <button type="button" class="btn btn--ghost btn--sm" data-complaint-action="edit" data-complaint-id="${c.id}">✏️ Edit</button>
            <button type="button" class="btn btn--ghost btn--sm btn--danger-text" data-complaint-action="delete" data-complaint-id="${c.id}">🗑️ Delete</button>
          </div>
        ` : ''}
      </div>
    `).join('');

    // Edit/delete handlers (idempotent — onclick replaces any prior handler)
    container.onclick = (event) => {
      const btn = event.target.closest('[data-complaint-action]');
      if (!btn) return;
      const id = btn.dataset.complaintId;
      const complaint = complaints.find(c => c.id === id);
      if (!complaint) return;
      if (btn.dataset.complaintAction === 'edit') {
        openComplaintEdit(complaint, container);
      } else if (btn.dataset.complaintAction === 'delete') {
        deleteOwnComplaint(id, container);
      }
    };
  } catch {
    container.innerHTML = `
      <div class="alert alert--error" role="alert">
        <span class="alert__icon">⚠️</span>
        <div class="alert__text">
          <strong>Network error</strong><br>
          Please check your connection and try again.
        </div>
      </div>
    `;
  }
}

/** Opens an inline edit form for a PENDING complaint. */
function openComplaintEdit(complaint, container) {
  const card = container.querySelector(`[data-complaint-id="${complaint.id}"]`)?.closest('.complaint-card');
  if (!card) return;

  card.innerHTML = `
    <form class="complaint-edit-form" data-complaint-edit-id="${complaint.id}" novalidate>
      <div class="form-group">
        <label class="form-label" for="ce-type-${complaint.id}">Complaint Type</label>
        <input type="text" id="ce-type-${complaint.id}" class="form-control" value="${escapeHtml(complaint.complaintType || '')}" required>
      </div>
      <div class="form-group">
        <label class="form-label" for="ce-desc-${complaint.id}">Description</label>
        <textarea id="ce-desc-${complaint.id}" class="form-control" rows="3" maxlength="1000" required>${escapeHtml(complaint.description || '')}</textarea>
      </div>
      <div class="complaint-card__actions">
        <button type="submit" class="btn btn--primary btn--sm">Save</button>
        <button type="button" class="btn btn--ghost btn--sm" data-complaint-cancel>Cancel</button>
      </div>
    </form>
  `;

  card.querySelector('[data-complaint-cancel]').addEventListener('click', () => renderMyComplaints(container));

  card.querySelector('form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const type = card.querySelector(`#ce-type-${complaint.id}`).value.trim();
    const desc = card.querySelector(`#ce-desc-${complaint.id}`).value.trim();
    if (!type || !desc) {
      Toast.warning('Missing fields', 'Complaint type and description are required.');
      return;
    }
    if (desc.length > 1000) {
      Toast.warning('Description too long', 'Description cannot exceed 1000 characters.');
      return;
    }
    const result = await API.updateComplaint(complaint.id, { complaintType: type, description: desc });
    if (result.ok && result.data?.success) {
      Toast.success('Complaint updated!', 'Your complaint has been saved.');
      renderMyComplaints(container);
    } else {
      Toast.error('Could not update complaint', result.data?.message || 'Please try again.');
    }
  });
}

/** Deletes the current user's own PENDING complaint after confirmation. */
async function deleteOwnComplaint(id, container) {
  if (!window.confirm('Delete this complaint? This cannot be undone.')) return;
  try {
    const result = await API.deleteComplaint(id);
    if (result.ok && result.data?.success) {
      Toast.success('Complaint deleted.', 'Your complaint has been removed.');
      renderMyComplaints(container);
    } else {
      Toast.error('Could not delete complaint', result.data?.message || 'Please try again.');
    }
  } catch {
    Toast.error('Network error', 'Please check your connection and try again.');
  }
}

/**
 * Initialises the "Raise a Complaint" form on the vendor detail page.
 * @param {string} vendorId - The vendor's ID
 * @param {HTMLElement} container - The element to render the form into
 */
async function initComplaintForm(vendorId, container) {
  if (!container) return;

  const currentUser = getCurrentUser();
  const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);

  // Logged out — ask the visitor to log in
  if (!token || !currentUser) {
    container.innerHTML = `
      <div class="alert alert--info" role="note">
        <span class="alert__icon">🔐</span>
        <div class="alert__text">
          <strong>Log in required</strong><br>
          Please <a href="login.html">log in</a> to raise a complaint about this vendor.
        </div>
      </div>
    `;
    return;
  }

  // Vendors cannot raise complaints (maintains authenticity)
  if (currentUser.role === 'VENDOR') {
    container.innerHTML = `
      <div class="alert alert--warning" role="note">
        <span class="alert__icon">⚠️</span>
        <div class="alert__text">
          <strong>Business accounts cannot raise complaints</strong><br>
          Vendor accounts are not allowed to raise complaints to maintain authenticity.
        </div>
      </div>
    `;
    return;
  }

  container.innerHTML = `
    <form id="complaint-form" novalidate aria-label="Raise a complaint">
      <div class="form-group">
        <label class="form-label" for="complaint-type">Complaint Type <span class="required" aria-label="required">*</span></label>
        <select id="complaint-type" name="complaintType" class="form-control" required>
          <option value="">Select a complaint type</option>
          <option value="Service">Service</option>
          <option value="Quality">Quality</option>
          <option value="Billing">Billing / Pricing</option>
          <option value="Hygiene">Hygiene</option>
          <option value="Behaviour">Staff Behaviour</option>
          <option value="Other">Other</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label" for="complaint-description">Description <span class="required" aria-label="required">*</span></label>
        <textarea id="complaint-description" name="description" class="form-control" rows="4"
                  maxlength="1000" placeholder="Describe your complaint…" required
                  aria-describedby="complaint-desc-hint"></textarea>
        <span class="form-hint" id="complaint-desc-hint">Required — maximum 1000 characters.</span>
      </div>
      <button type="submit" class="btn btn--primary">Submit Complaint</button>
    </form>
  `;

  const form = document.getElementById('complaint-form');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const type = document.getElementById('complaint-type').value;
    const description = document.getElementById('complaint-description').value.trim();

    if (!type) {
      Toast.warning('Complaint type required', 'Please select a complaint type.');
      return;
    }
    if (!description) {
      Toast.warning('Description required', 'Please describe your complaint.');
      return;
    }
    if (description.length > 1000) {
      Toast.warning('Description too long', 'Description cannot exceed 1000 characters.');
      return;
    }

    const submitBtn = form.querySelector('button[type="submit"]');
    const originalText = submitBtn.textContent;
    submitBtn.disabled = true;
    submitBtn.textContent = 'Submitting…';

    try {
      const result = await API.createComplaint({ vendorId, complaintType: type, description });
      if (result.ok && result.data?.success) {
        form.innerHTML = `
          <div class="alert alert--success" role="status">
            <span class="alert__icon">✅</span>
            <div class="alert__text">
              <strong>Complaint submitted!</strong><br>
              The vendor has been notified. You can track its status from your profile.
            </div>
          </div>
        `;
        Toast.success('Complaint submitted!', 'The vendor has been notified.');
      } else {
        Toast.error('Submission failed', result.data?.message || 'Please try again.');
      }
    } catch {
      Toast.error('Network error', 'Please check your connection and try again.');
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = originalText;
    }
  });
}

/** Minimal HTML escaping helper (mirrors existing frontend utilities). */
function escapeHtml(value) {
  // \xNN escapes are used for the entity characters so an editor/formatter
  // cannot decode the HTML entities back into raw characters.
  return String(value == null ? '' : value)
    .replace(/&/g, '\x26amp;')
    .replace(/</g, '\x26lt;')
    .replace(/>/g, '\x26gt;')
    .replace(/"/g, '\x26quot;')
    .replace(/'/g, '\x26#39;');
}
