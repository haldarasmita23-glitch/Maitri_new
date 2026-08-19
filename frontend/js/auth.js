/**
 * Maitri — Auth Pages JavaScript
 *
 * Phase 3B: Client-side validation, authentication API calls, and session state.
 */

const AuthSession = {
  token() {
    return localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  },

  save(authData) {
    localStorage.setItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN, authData.token);
    localStorage.setItem(CONFIG.STORAGE_KEYS.USER_DATA, JSON.stringify(authData.user));
    window.dispatchEvent(new CustomEvent('maitri:auth-change', { detail: authData.user }));
  },

  clear() {
    localStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
    localStorage.removeItem(CONFIG.STORAGE_KEYS.USER_DATA);
    window.dispatchEvent(new CustomEvent('maitri:auth-change', { detail: null }));
  },

  async restore() {
    if (!this.token()) return null;

    try {
      const result = await API.getCurrentUser();
      if (result.ok && result.data?.success && result.data.data) {
        localStorage.setItem(CONFIG.STORAGE_KEYS.USER_DATA, JSON.stringify(result.data.data));
        window.dispatchEvent(new CustomEvent('maitri:auth-change', { detail: result.data.data }));
        return result.data.data;
      }
      if (result.status === 401) this.clear();
    } catch {
      // Keep the local session during a temporary network outage.
    }
    return null;
  },

  logout() {
    this.clear();
    window.location.href = '../index.html';
  },
};

document.addEventListener('DOMContentLoaded', () => {
  initPasswordToggles();
  initLoginForm();
  initRegisterForm();
  initVendorRegisterForm();
  initRoleTabs();
});


// ── Password visibility toggle ─────────────────────────────────

function initPasswordToggles() {
  document.querySelectorAll('.btn-toggle-password').forEach(btn => {
    btn.addEventListener('click', () => {
      const input = btn.previousElementSibling;
      if (!input) return;
      const isText = input.type === 'text';
      input.type = isText ? 'password' : 'text';
      btn.textContent = isText ? '👁️' : '🙈';
      btn.setAttribute('aria-label', isText ? 'Show password' : 'Hide password');
    });
  });
}


// ── Login Form ─────────────────────────────────────────────────

function initLoginForm() {
  const form = document.getElementById('login-form');
  if (!form) return;

  form.addEventListener('submit', async e => {
    e.preventDefault();
    clearErrors(form);

    const email    = form.querySelector('#login-email')?.value.trim();
    const password = form.querySelector('#login-password')?.value;

    let valid = true;

    if (!email || !isValidEmail(email)) {
      showFieldError('login-email', 'Please enter a valid email address.');
      valid = false;
    }
    if (!password || password.length < 6) {
      showFieldError('login-password', 'Password must be at least 6 characters.');
      valid = false;
    }
    if (!valid) return;

    const btn = form.querySelector('button[type="submit"]');
    setLoading(btn, true);
    try {
      const result = await API.login({ email, password });
      if (!result.ok || !result.data?.success) {
        Toast.error('Login failed', apiErrorMessage(result, 'Invalid email or password.'));
        return;
      }

      AuthSession.save(result.data.data);
      window.location.href = '../index.html';
    } catch {
      Toast.error('Unable to log in', 'Please check your connection and try again.');
    } finally {
      setLoading(btn, false);
    }
  });
}


// ── Register Form ──────────────────────────────────────────────

function initRegisterForm() {
  const form = document.getElementById('register-form');
  if (!form) return;

  form.addEventListener('submit', async e => {
    e.preventDefault();
    clearErrors(form);

    const name     = form.querySelector('#reg-name')?.value.trim();
    const email    = form.querySelector('#reg-email')?.value.trim();
    const password = form.querySelector('#reg-password')?.value;
    const confirm  = form.querySelector('#reg-confirm')?.value;
    const agree    = form.querySelector('#reg-agree')?.checked;

    let valid = true;

    if (!name || name.length < 2) {
      showFieldError('reg-name', 'Full name must be at least 2 characters.');
      valid = false;
    }
    if (!email || !isValidEmail(email)) {
      showFieldError('reg-email', 'Please enter a valid email address.');
      valid = false;
    }
    if (!password || password.length < 8) {
      showFieldError('reg-password', 'Password must be at least 8 characters.');
      valid = false;
    }
    if (password !== confirm) {
      showFieldError('reg-confirm', 'Passwords do not match.');
      valid = false;
    }
    if (!agree) {
      Toast.warning('Agreement required', 'Please accept the Terms of Service to continue.');
      valid = false;
    }
    if (!valid) return;

    const btn = form.querySelector('button[type="submit"]');
    setLoading(btn, true);
    try {
      const result = await API.register({ name, email, password, role: 'USER' });
      if (!result.ok || !result.data?.success) {
        Toast.error('Registration failed', apiErrorMessage(result, 'Please review your details and try again.'));
        return;
      }

      AuthSession.save(result.data.data);
      window.location.href = '../index.html';
    } catch {
      Toast.error('Unable to create account', 'Please check your connection and try again.');
    } finally {
      setLoading(btn, false);
    }
  });
}


// ── Vendor Register Form ───────────────────────────────────────

function initVendorRegisterForm() {
  const form = document.getElementById('vendor-register-form');
  if (!form) return;

  // Multi-step logic
  const steps = form.querySelectorAll('.vendor-step');
  const stepDots = document.querySelectorAll('.auth-step-dot');
  const stepLines = document.querySelectorAll('.auth-step-line');
  let currentStep = 0;

  function showStep(idx) {
    steps.forEach((s, i) => s.classList.toggle('hidden', i !== idx));
    stepDots.forEach((dot, i) => {
      dot.classList.toggle('active', i === idx);
      dot.classList.toggle('done', i < idx);
      dot.textContent = i < idx ? '✓' : i + 1;
    });
    stepLines.forEach((line, i) => {
      line.classList.toggle('done', i < idx);
    });
    currentStep = idx;
  }

  showStep(0);

  // Next buttons
  form.querySelectorAll('[data-next-step]').forEach(btn => {
    btn.addEventListener('click', () => {
      if (validateStep(form, currentStep)) {
        showStep(currentStep + 1);
      }
    });
  });

  // Back buttons
  form.querySelectorAll('[data-prev-step]').forEach(btn => {
    btn.addEventListener('click', () => showStep(currentStep - 1));
  });

  // Submit
  form.addEventListener('submit', async e => {
    e.preventDefault();
    if (!validateStep(form, currentStep)) return;

    // Password checks (required beyond the generic required-field validation)
    const password = document.getElementById('vr-password')?.value;
    const confirm  = document.getElementById('vr-confirm')?.value;
    let valid = true;
    if (!password || password.length < 8) {
      showFieldError('vr-password', 'Password must be at least 8 characters.');
      valid = false;
    }
    if (password !== confirm) {
      showFieldError('vr-confirm', 'Passwords do not match.');
      valid = false;
    }
    if (!valid) return;

    const category = document.querySelector('input[name="category"]:checked')?.value;

    const payload = {
      shopName:     document.getElementById('vr-shop-name')?.value.trim(),
      ownerName:    document.getElementById('vr-owner-name')?.value.trim(),
      categoryId:   category,
      description:  document.getElementById('vr-description')?.value.trim(),
      address:      document.getElementById('vr-address')?.value.trim(),
      area:         document.getElementById('vr-area')?.value,
      phone:        document.getElementById('vr-phone')?.value.trim(),
      openingTime:  document.getElementById('vr-open-time')?.value,
      closingTime:  document.getElementById('vr-close-time')?.value,
    };

    const email = document.getElementById('vr-email')?.value.trim();

    const btn = form.querySelector('button[type="submit"]');
    setLoading(btn, true);
    try {
      // 1. Create the VENDOR account (role=VENDOR is allowed via public register)
      const account = await API.register({
        name: payload.ownerName,
        email,
        password,
        role: 'VENDOR',
      });
      if (!account.ok || !account.data?.success) {
        Toast.error('Account creation failed', apiErrorMessage(account, 'Please review your details and try again.'));
        return;
      }
      AuthSession.save(account.data.data);

      // 2. Submit the business listing (status → PENDING)
      const listing = await API.applyVendor(payload);
      if (!listing.success) {
        Toast.error('Listing submission failed', listing.message || 'Please try again.');
        return;
      }

      // 3. Success — replace the form with the success panel
      form.style.display = 'none';
      const success = document.getElementById('vendor-success');
      if (success) success.style.display = 'block';
      Toast.success('Application submitted!', 'Your business is now pending review.');
    } catch {
      Toast.error('Unable to submit', 'Please check your connection and try again.');
    } finally {
      setLoading(btn, false);
    }
  });
}


function validateStep(form, step) {
  clearErrors(form);
  const stepEl = form.querySelectorAll('.vendor-step')[step];
  if (!stepEl) return true;

  const required = stepEl.querySelectorAll('[required]');
  let valid = true;

  required.forEach(field => {
    if (!field.value.trim()) {
      showFieldError(field.id, 'This field is required.');
      valid = false;
    }
  });

  // Email validation
  const emailField = stepEl.querySelector('input[type="email"]');
  if (emailField?.value && !isValidEmail(emailField.value)) {
    showFieldError(emailField.id, 'Please enter a valid email address.');
    valid = false;
  }

  return valid;
}


// ── Role Tabs (login page — User / Vendor / Admin) ─────────────

function initRoleTabs() {
  const tabs = document.querySelectorAll('.auth-role-tab');
  if (!tabs.length) return;

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => {
        t.classList.remove('active');
        t.setAttribute('aria-selected', 'false');
      });
      tab.classList.add('active');
      tab.setAttribute('aria-selected', 'true');
    });
  });
}


// ── Utility Helpers ────────────────────────────────────────────

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function apiErrorMessage(result, fallback) {
  const response = result?.data;
  if (Array.isArray(response?.errors) && response.errors.length) {
    return response.errors.join(' ');
  }
  return response?.message || fallback;
}

function showFieldError(fieldId, message) {
  const field = document.getElementById(fieldId);
  if (!field) return;
  field.classList.add('form-control--error');

  const errId = `${fieldId}-error`;
  let errEl = document.getElementById(errId);
  if (!errEl) {
    errEl = document.createElement('div');
    errEl.id = errId;
    errEl.className = 'form-error';
    errEl.setAttribute('role', 'alert');
    field.parentNode.insertBefore(errEl, field.nextSibling);
  }
  errEl.textContent = message;
  field.setAttribute('aria-describedby', errId);
  field.setAttribute('aria-invalid', 'true');
}

function clearErrors(form) {
  form.querySelectorAll('.form-control--error').forEach(f => {
    f.classList.remove('form-control--error');
    f.removeAttribute('aria-invalid');
    f.removeAttribute('aria-describedby');
  });
  form.querySelectorAll('.form-error').forEach(e => e.remove());
}

function setLoading(btn, loading) {
  if (!btn) return;
  btn.disabled = loading;
  btn.classList.toggle('btn--loading', loading);
  if (!loading) btn.disabled = false;
}
