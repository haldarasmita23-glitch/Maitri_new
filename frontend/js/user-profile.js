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
  hi: 'Hindi',
  kn: 'Kannada',
};

const ROLE_LABELS = {
  USER: 'Member',
  VENDOR: 'Vendor',
  ADMIN: 'Admin',
};

document.addEventListener('DOMContentLoaded', () => {
  initUserProfile();
});

async function initUserProfile() {
  const contentEl = document.getElementById('profile-content');
  const loginRequiredEl = document.getElementById('profile-login-required');

  const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  if (!token) {
    showProfileView(loginRequiredEl, contentEl);
    return;
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

  showProfileView(contentEl, loginRequiredEl);
  renderProfile(profile);
  bindProfileForm();
}

// ── Rendering ───────────────────────────────────────────────────

function showProfileView(showEl, hideEl) {
  showEl.classList.remove('hidden');
  hideEl.classList.add('hidden');
}

function readStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA)) || null;
  } catch {
    return null;
  }
}

function renderProfile(user) {
  const name = user.name || user.email || 'Maitri Member';
  const location = user.location || {};
  const lang = user.preferredLanguage || 'en';
  const role = user.role || 'USER';
  const photo = user.profilePhoto || '';

  document.getElementById('profile-summary-name').textContent = name;
  document.getElementById('profile-summary-email').textContent = user.email || '—';
  document.getElementById('profile-summary-role').textContent = ROLE_LABELS[role] || role;
  document.getElementById('profile-summary-lang').textContent = `🌐 ${LANGUAGES[lang] || lang}`;
  document.getElementById('profile-summary-phone').textContent = user.phone || 'Not set';
  document.getElementById('profile-summary-area').textContent = location.area || 'Not set';
  document.getElementById('profile-summary-city').textContent = location.city || 'Not set';

  const joinedEl = document.getElementById('profile-summary-joined');
  if (user.createdAt) {
    try {
      const date = new Date(user.createdAt);
      if (!Number.isNaN(date.getTime())) {
        joinedEl.textContent = `Joined ${date.toLocaleDateString(undefined, { month: 'short', year: 'numeric' })}`;
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
        Toast.error('Could not save changes', apiErrorMessage(result, 'Please review your details and try again.'));
        return;
      }

      const updated = result.data.data;
      AuthSession.save({ token: AuthSession.token(), user: updated });
      renderProfile(updated);
      Toast.success('Profile updated!', 'Your changes have been saved.');
    } catch {
      Toast.error('Unable to save changes', 'Please check your connection and try again.');
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