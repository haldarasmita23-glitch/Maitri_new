/**
 * Maitri — My Profile Page (Phase 6)
 *
 * Renders the authenticated user's profile and lets them edit their
 * editable fields via GET/PUT /api/users/me. After a successful update the
 * local AuthSession is refreshed so the navbar greeting stays current.
 *
 * Auth guard: users without a session see a "log in required" panel instead
 * of the form. A 401 during load (expired token) clears the session.
 */

const LANGUAGES = {
  en: 'English',
  hi: 'हिन्दी',
  kn: 'ಕನ್ನಡ',
};

function getRoleLabel(role) {
  if (typeof I18n !== 'undefined') {
    if (role === 'ADMIN') return I18n.t('profile.roleAdmin');
    if (role === 'VENDOR') return I18n.t('profile.roleVendor');
    return I18n.t('profile.roleMember');
  }
  return role === 'ADMIN' ? 'Admin' : (role === 'VENDOR' ? 'Vendor' : 'Member');
}

let _currentProfile = null;

document.addEventListener('DOMContentLoaded', () => {
  initUserProfile();
  window.addEventListener('maitri:language-change', () => {
    if (_currentProfile) {
      renderProfile(_currentProfile);
    }
  });
});

async function initUserProfile() {
  const contentEl = document.getElementById('profile-content');
  const loginRequiredEl = document.getElementById('profile-login-required');

  const token = sessionStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN) || localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  if (!token) {
    showProfileView(loginRequiredEl, contentEl);
    return;
  }

  // Role Guard: Vendors and Admins use their dedicated business/admin portals
  if (typeof AuthSession !== 'undefined') {
    if (AuthSession.isVendor()) {
      window.location.href = 'vendor-dashboard.html';
      return;
    }
    if (AuthSession.isAdmin()) {
      window.location.href = 'admin.html';
      return;
    }
  }

  let profile = null;
  try {
    const result = await API.getUserProfile();
    if (result.ok && result.data?.success && result.data.data) {
      profile = result.data.data;
    } else if (result.status === 401) {
      AuthSession.clear();
      showProfileView(loginRequiredEl, contentEl);
      return;
    }
  } catch {
    // Backend unreachable — fall back to the locally stored user data so the
    // page still renders. Editing requires the backend and will show a toast.
    profile = null;
  }

  if (!profile) {
    profile = readStoredUser();
    if (!profile) {
      showProfileView(loginRequiredEl, contentEl);
      return;
    }
  }

  _currentProfile = profile;
  showProfileView(contentEl, loginRequiredEl);
  renderProfile(profile);
  bindProfileForm();

  // Phase 9 — My Complaints (only for USER/ADMIN accounts; VENDOR accounts
  // cannot raise complaints, so the section renders a role-specific message).
  const complaintsContainer = document.getElementById('my-complaints-container');
  if (complaintsContainer) {
    const role = (profile && profile.role) || (readStoredUser() && readStoredUser().role) || 'USER';
    if (role === 'VENDOR') {
      complaintsContainer.innerHTML = `
        <div class="alert alert--warning" role="note">
          <span class="alert__icon">⚠️</span>
          <div class="alert__text">
            <strong>Not available for business accounts</strong><br>
            Vendor accounts cannot raise complaints.
          </div>
        </div>
      `;
    } else {
      renderMyComplaints(complaintsContainer);
    }
  }
}

// ── Rendering ───────────────────────────────────────────────────

function showProfileView(showEl, hideEl) {
  showEl.classList.remove('hidden');
  hideEl.classList.add('hidden');
}

function readStoredUser() {
  try {
    return JSON.parse(sessionStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA) || localStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA)) || null;
  } catch {
    return null;
  }
}

function renderProfile(user) {
  const notSetLabel = typeof I18n !== 'undefined' ? I18n.t('profile.notSet') : 'Not set';
  const name = user.name || user.email || 'Maitri Member';
  const location = user.location || {};
  const lang = user.preferredLanguage || 'en';
  const role = user.role || 'USER';
  const photo = user.profilePhoto || '';

  document.getElementById('profile-summary-name').textContent = name;
  document.getElementById('profile-summary-email').textContent = user.email || '—';
  document.getElementById('profile-summary-role').textContent = getRoleLabel(role);
  document.getElementById('profile-summary-lang').textContent = `🌐 ${LANGUAGES[lang] || lang}`;
  document.getElementById('profile-summary-phone').textContent = user.phone || notSetLabel;
  document.getElementById('profile-summary-area').textContent = location.area || notSetLabel;
  document.getElementById('profile-summary-city').textContent = location.city || notSetLabel;

  const joinedEl = document.getElementById('profile-summary-joined');
  if (user.createdAt) {
    try {
      const date = new Date(user.createdAt);
      if (!Number.isNaN(date.getTime())) {
        const formattedDate = date.toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
        joinedEl.textContent = typeof I18n !== 'undefined'
          ? I18n.t('profile.joinedOn', { date: formattedDate })
          : `Joined ${formattedDate}`;
      }
    } catch {
      joinedEl.textContent = '';
    }
  } else {
    joinedEl.textContent = '';
  }

  const img = document.getElementById('profile-avatar-img');
  const fallback = document.getElementById('profile-avatar-fallback');
  if (photo) {
    img.src = photo;
    img.classList.remove('hidden');
    fallback.classList.add('hidden');
  } else {
    img.removeAttribute('src');
    img.classList.add('hidden');
    fallback.classList.remove('hidden');
  }

  // Form
  document.getElementById('pf-name').value = user.name || '';
  document.getElementById('pf-email').value = user.email || '';
  document.getElementById('pf-phone').value = user.phone || '';
  document.getElementById('pf-language').value = LANGUAGES[lang] ? lang : 'en';
  document.getElementById('pf-area').value = location.area || '';
  document.getElementById('pf-city').value = location.city || '';
  document.getElementById('pf-photo').value = photo;
}

// ── Form submission ─────────────────────────────────────────────

function bindProfileForm() {
  const form = document.getElementById('profile-form');
  if (!form) return;

  form.addEventListener('submit', async e => {
    e.preventDefault();
    clearErrors(form);

    const payload = collectPayload(form);
    if (!payload) return;

    const btn = form.querySelector('button[type="submit"]');
    setLoading(btn, true);
    try {
      const result = await API.updateUserProfile(payload);
      if (!result.ok || !result.data?.success) {
        Toast.error(
          typeof I18n !== 'undefined' ? I18n.t('messages.profileUpdateFailed') : 'Could not save changes',
          apiErrorMessage(result, typeof I18n !== 'undefined' ? I18n.t('messages.profileUpdateFailed') : 'Please review your details and try again.')
        );
        return;
      }

      const updated = result.data.data;
      _currentProfile = updated;
      AuthSession.save({ token: AuthSession.token(), user: updated });
      if (typeof I18n !== 'undefined' && updated.preferredLanguage) {
        I18n.setLanguage(updated.preferredLanguage, false);
      }
      renderProfile(updated);
      Toast.success(
        typeof I18n !== 'undefined' ? I18n.t('messages.profileUpdated') : 'Profile updated!',
        typeof I18n !== 'undefined' ? I18n.t('common.saveChanges') : 'Your changes have been saved.'
      );
    } catch {
      Toast.error(
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Unable to save changes',
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Please check your connection and try again.'
      );
    } finally {
      setLoading(btn, false);
    }
  });
}

function collectPayload(form) {
  const name = form.querySelector('#pf-name')?.value.trim();
  const phone = form.querySelector('#pf-phone')?.value.trim();
  const language = form.querySelector('#pf-language')?.value || 'en';
  const area = form.querySelector('#pf-area')?.value.trim();
  const city = form.querySelector('#pf-city')?.value.trim();
  const photo = form.querySelector('#pf-photo')?.value.trim();

  let valid = true;

  if (!name || name.length < 2) {
    showFieldError('pf-name', 'Full name must be at least 2 characters.');
    valid = false;
  }
  if (phone && !/^[6-9][0-9]{9}$/.test(phone)) {
    showFieldError('pf-phone', 'Enter a valid 10-digit mobile number.');
    valid = false;
  }
  if (photo && !/^https?:\/\/.+/.test(photo)) {
    showFieldError('pf-photo', 'Profile photo must be a valid http(s) URL.');
    valid = false;
  }
  if (!valid) return null;

  return {
    name,
    phone: phone || null,
    preferredLanguage: language,
    location: { area: area || null, city: city || null },
    profilePhoto: photo || null,
  };
}