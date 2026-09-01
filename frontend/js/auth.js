/**
 * Maitri — Auth Pages JavaScript
 *
 * Phase 3B: Client-side validation, authentication API calls, and session state.
 */

const AuthSession = {
  normalizeRole(role) {
    if (!role) return null;
    const s = String(role).trim().toUpperCase();
    if (s === 'ROLE_VENDOR' || s === 'VENDOR') return 'VENDOR';
    if (s === 'ROLE_ADMIN' || s === 'ADMIN' || s === 'ROLE_SUPER_ADMIN' || s === 'SUPER_ADMIN') return 'ADMIN';
    if (s === 'ROLE_USER' || s === 'USER' || s === 'MEMBER') return 'USER';
    return s.replace(/^ROLE_/, '');
  },

  token() {
    return localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  },

  user() {
    try {
      const u = JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA));
      if (u && u.role) {
        u.role = this.normalizeRole(u.role);
      }
      return u;
    } catch {
      return null;
    }
  },

  getUser() {
    return this.user();
  },

  role() {
    const u = this.user();
    return u && u.role ? this.normalizeRole(u.role) : null;
  },

  getRole() {
    return this.role();
  },

  isLoggedIn() {
    return !!this.token() && !!this.user();
  },

  isUser() {
    return this.getRole() === 'USER';
  },

  isVendor() {
    return this.getRole() === 'VENDOR';
  },

  isAdmin() {
    return this.getRole() === 'ADMIN';
  },

  /**
   * Enforces role requirement on page entry.
   * If not logged in or role not permitted, redirects to appropriate page.
   * @param {string|string[]} allowedRoles - Role or array of allowed roles
   * @param {string} [redirectUrl] - Custom fallback redirect URL
   * @returns {boolean} true if permitted, false if redirecting
   */
  requireRole(allowedRoles, redirectUrl) {
    const isPages = window.location.pathname.includes('/pages/');
    const rawRoles = Array.isArray(allowedRoles) ? allowedRoles : [allowedRoles];
    const roles = rawRoles.map(r => this.normalizeRole(r));

    if (!this.isLoggedIn()) {
      const target = redirectUrl || (
        roles.includes('VENDOR') ? (isPages ? 'vendor-login.html' : 'pages/vendor-login.html') :
        roles.includes('ADMIN') ? (isPages ? 'admin-login.html' : 'pages/admin-login.html') :
        (isPages ? 'login.html' : 'pages/login.html')
      );
      window.location.href = target;
      return false;
    }

    const current = this.getRole();
    if (!roles.includes(current)) {
      const target = redirectUrl || (
        current === 'VENDOR' ? (isPages ? 'vendor-dashboard.html' : 'pages/vendor-dashboard.html') :
        current === 'ADMIN' ? (isPages ? 'admin.html' : 'pages/admin.html') :
        (isPages ? '../index.html' : 'index.html')
      );
      window.location.href = target;
      return false;
    }
    return true;
  },

  save(authData) {
    if (authData && authData.user && authData.user.role) {
      authData.user.role = this.normalizeRole(authData.user.role);
    }
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
        const u = result.data.data;
        if (u.role) u.role = this.normalizeRole(u.role);
        localStorage.setItem(CONFIG.STORAGE_KEYS.USER_DATA, JSON.stringify(u));
        window.dispatchEvent(new CustomEvent('maitri:auth-change', { detail: u }));
        return u;
      }
      if (result.status === 401) this.clear();
    } catch {
      // Keep the local session during a temporary network outage.
    }
    return null;
  },

  logout() {
    this.clear();
    window.location.href = window.location.pathname.includes('/pages/') ? '../index.html' : 'index.html';
  },
};

document.addEventListener('DOMContentLoaded', () => {
  initPasswordToggles();
  initLoginForm();
  initVendorLoginForm();
  initAdminLoginForm();
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


// ── Login Form (Customer / Community Member) ───────────────────

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

      const authData = result.data.data;
      AuthSession.save(authData);
      if (typeof I18n !== 'undefined' && authData.user?.preferredLanguage) {
        I18n.setLanguage(authData.user.preferredLanguage, false);
      }
      
      const role = AuthSession.normalizeRole(authData.user?.role);
      const searchParams = new URLSearchParams(window.location.search);
      const redirectUrl = searchParams.get('redirect');
      if (redirectUrl) {
        window.location.href = decodeURIComponent(redirectUrl);
      } else if (role === 'VENDOR') {
        window.location.href = 'vendor-dashboard.html';
      } else if (role === 'ADMIN') {
        window.location.href = 'admin.html';
      } else {
        window.location.href = '../index.html';
      }
    } catch {
      Toast.error(
        typeof I18n !== 'undefined' ? I18n.t('messages.loginFailed') : 'Unable to log in',
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Please check your connection and try again.'
      );
    } finally {
      setLoading(btn, false);
    }
  });
}


// ── Vendor Login Form (Business Owners) ─────────────────────────

function initVendorLoginForm() {
  const form = document.getElementById('vendor-login-form');
  if (!form) return;

  form.addEventListener('submit', async e => {
    e.preventDefault();
    clearErrors(form);

    const email    = form.querySelector('#vendor-email')?.value.trim();
    const password = form.querySelector('#vendor-password')?.value;

    let valid = true;

    if (!email || !isValidEmail(email)) {
      showFieldError('vendor-email', 'Please enter a valid business email address.');
      valid = false;
    }
    if (!password || password.length < 6) {
      showFieldError('vendor-password', 'Password must be at least 6 characters.');
      valid = false;
    }
    if (!valid) return;

    const btn = form.querySelector('button[type="submit"]');
    setLoading(btn, true);
    try {
      const result = await API.login({ email, password });
      if (!result.ok || !result.data?.success) {
        Toast.error(
          typeof I18n !== 'undefined' ? I18n.t('messages.loginFailed') : 'Login failed',
          apiErrorMessage(result, 'Invalid email or password.')
        );
        return;
      }

      const authData = result.data.data;
      AuthSession.save(authData);
      if (typeof I18n !== 'undefined' && authData.user?.preferredLanguage) {
        I18n.setLanguage(authData.user.preferredLanguage, false);
      }

      const role = AuthSession.normalizeRole(authData.user?.role);
      const searchParams = new URLSearchParams(window.location.search);
      const redirectUrl = searchParams.get('redirect');

      if (redirectUrl) {
        window.location.href = decodeURIComponent(redirectUrl);
      } else if (role === 'VENDOR' || role === 'ADMIN') {
        window.location.href = 'vendor-dashboard.html';
      } else {
        // Community Member logged in via vendor portal
        Toast.info(
          'Resident Account',
          typeof I18n !== 'undefined'
            ? I18n.t('vendorLogin.notVendorWarning')
            : 'You are signed in as a community member. Redirecting to home…'
        );
        setTimeout(() => {
          window.location.href = '../index.html';
        }, 1200);
      }
    } catch {
      Toast.error(
        typeof I18n !== 'undefined' ? I18n.t('messages.loginFailed') : 'Unable to log in',
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Please check your connection and try again.'
      );
    } finally {
      setLoading(btn, false);
    }
  });
}


// ── Admin Login Form (Platform Administrators) ──────────────────

function initAdminLoginForm() {
  const form = document.getElementById('admin-login-form');
  if (!form) return;

  form.addEventListener('submit', async e => {
    e.preventDefault();
    clearErrors(form);

    const email    = form.querySelector('#admin-email')?.value.trim();
    const password = form.querySelector('#admin-password')?.value;

    let valid = true;

    if (!email || !isValidEmail(email)) {
      showFieldError('admin-email', 'Please enter a valid administrator email address.');
      valid = false;
    }
    if (!password || password.length < 6) {
      showFieldError('admin-password', 'Password must be at least 6 characters.');
      valid = false;
    }
    if (!valid) return;

    const btn = form.querySelector('button[type="submit"]');
    setLoading(btn, true);
    try {
      const result = await API.login({ email, password });
      if (!result.ok || !result.data?.success) {
        Toast.error('Authentication Failed', apiErrorMessage(result, 'Invalid administrator credentials.'));
        return;
      }

      const authData = result.data.data;
      const role = AuthSession.normalizeRole(authData.user?.role);

      // Strict role check: Only ADMIN and SUPER_ADMIN allowed
      if (role !== 'ADMIN') {
        AuthSession.clear();
        showFieldError(
          'admin-email',
          typeof I18n !== 'undefined'
            ? I18n.t('adminLogin.accessDenied')
            : 'Access Denied: Administrator privileges required for this portal.'
        );
        Toast.error(
          'Access Denied',
          typeof I18n !== 'undefined'
            ? I18n.t('adminLogin.accessDenied')
            : 'Access Denied: Administrator privileges required for this portal.'
        );
        return;
      }

      AuthSession.save(authData);
      if (typeof I18n !== 'undefined' && authData.user?.preferredLanguage) {
        I18n.setLanguage(authData.user.preferredLanguage, false);
      }

      const searchParams = new URLSearchParams(window.location.search);
      const redirectUrl = searchParams.get('redirect');
      if (redirectUrl) {
        window.location.href = decodeURIComponent(redirectUrl);
      } else {
        window.location.href = 'admin.html';
      }
    } catch {
      Toast.error(
        typeof I18n !== 'undefined' ? I18n.t('messages.loginFailed') : 'Authentication Failed',
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Please check your connection and try again.'
      );
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
      showFieldError('reg-name', typeof I18n !== 'undefined' ? I18n.t('validation.nameRequired') : 'Full name must be at least 2 characters.');
      valid = false;
    }
    if (!email || !isValidEmail(email)) {
      showFieldError('reg-email', typeof I18n !== 'undefined' ? I18n.t('validation.emailRequired') : 'Please enter a valid email address.');
      valid = false;
    }
    if (!password || password.length < 8) {
      showFieldError('reg-password', typeof I18n !== 'undefined' ? I18n.t('validation.passwordLength8') : 'Password must be at least 8 characters.');
      valid = false;
    }
    if (password !== confirm) {
      showFieldError('reg-confirm', typeof I18n !== 'undefined' ? I18n.t('validation.passwordsMatch') : 'Passwords do not match.');
      valid = false;
    }
    if (!agree) {
      Toast.warning(
        'Agreement required',
        typeof I18n !== 'undefined' ? I18n.t('validation.termsRequired') : 'Please accept the Terms of Service to continue.'
      );
      valid = false;
    }
    if (!valid) return;

    const btn = form.querySelector('button[type="submit"]');
    setLoading(btn, true);
    try {
      const currentLang = typeof I18n !== 'undefined' ? I18n.getLanguage() : 'en';
      const result = await API.register({ name, email, password, role: 'USER', preferredLanguage: currentLang });
      if (!result.ok || !result.data?.success) {
        Toast.error('Registration failed', apiErrorMessage(result, 'Please review your details and try again.'));
        return;
      }

      const authData = result.data.data;
      AuthSession.save(authData);
      if (typeof I18n !== 'undefined' && authData.user?.preferredLanguage) {
        I18n.setLanguage(authData.user.preferredLanguage, false);
      }
      window.location.href = '../index.html';
    } catch {
      Toast.error(
        typeof I18n !== 'undefined' ? I18n.t('messages.signupFailed') : 'Unable to create account',
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Please check your connection and try again.'
      );
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

    const rawCategory = document.getElementById('vr-category')?.value?.trim() ||
                        document.querySelector('input[name="category"]:checked')?.value || '';

    const rawOpenTime = document.getElementById('vr-open-time')?.value || '';
    const rawCloseTime = document.getElementById('vr-close-time')?.value || '';
    const rawPhone = document.getElementById('vr-phone')?.value || '';

    // Normalize time to HH:mm format expected by backend regex ^([01]\d|2[0-3]):[0-5]\d$
    const formatTimeHHmm = t => {
      if (!t) return '';
      const match = t.trim().match(/^(\d{1,2}):(\d{2})/);
      return match ? `${match[1].padStart(2, '0')}:${match[2]}` : t.trim();
    };

    // Clean phone number to 10 digits
    const cleanPhoneDigits = p => {
      const digits = p.replace(/\D/g, '');
      return digits.length > 10 ? digits.slice(-10) : digits;
    };

    const payload = {
      shopName:     document.getElementById('vr-shop-name')?.value.trim(),
      ownerName:    document.getElementById('vr-owner-name')?.value.trim(),
      categoryId:   rawCategory,
      description:  document.getElementById('vr-description')?.value.trim(),
      address:      document.getElementById('vr-address')?.value.trim(),
      area:         document.getElementById('vr-area')?.value,
      phone:        cleanPhoneDigits(rawPhone),
      openingTime:  formatTimeHHmm(rawOpenTime),
      closingTime:  formatTimeHHmm(rawCloseTime),
    };

    const email = document.getElementById('vr-email')?.value.trim();

    // Client-side cross-step validation sanity checks
    if (!payload.shopName) {
      showFieldError('vr-shop-name', 'Shop name is required.');
      showStep(0);
      return;
    }
    if (!payload.categoryId) {
      showFieldError('vr-category', 'Please select a business category.');
      showStep(0);
      return;
    }
    if (!payload.description || payload.description.length < 20) {
      showFieldError('vr-description', 'Business description must be at least 20 characters.');
      showStep(0);
      return;
    }
    if (!payload.area) {
      showFieldError('vr-area', 'Please select your area.');
      showStep(1);
      return;
    }
    if (!payload.address) {
      showFieldError('vr-address', 'Full address is required.');
      showStep(1);
      return;
    }
    if (!payload.openingTime || !payload.closingTime) {
      if (!payload.openingTime) showFieldError('vr-open-time', 'Opening time is required.');
      if (!payload.closingTime) showFieldError('vr-close-time', 'Closing time is required.');
      showStep(1);
      return;
    }
    if (!payload.ownerName) {
      showFieldError('vr-owner-name', 'Owner name is required.');
      showStep(2);
      return;
    }
    if (!payload.phone || payload.phone.length !== 10 || !/^[6-9][0-9]{9}$/.test(payload.phone)) {
      showFieldError('vr-phone', 'Please enter a valid 10-digit Indian mobile number.');
      showStep(2);
      return;
    }

    const btn = form.querySelector('button[type="submit"]');
    setLoading(btn, true);
    try {
      // 1. Create the VENDOR account or authenticate if already registered in a previous attempt
      let token = AuthSession.token();
      const currentUser = AuthSession.user();

      if (!token || currentUser?.email?.toLowerCase() !== email.toLowerCase()) {
        const account = await API.register({
          name: payload.ownerName,
          email,
          password,
          role: 'VENDOR',
        });

        if (!account.ok || !account.data?.success) {
          // If the user was already created (e.g. from an earlier submit where listing failed), attempt login
          if (account.status === 409) {
            const loginRes = await API.login({ email, password });
            if (loginRes.ok && loginRes.data?.success) {
              AuthSession.save(loginRes.data.data);
              token = loginRes.data.data.token;
            } else {
              Toast.error(
                typeof I18n !== 'undefined' ? I18n.t('messages.signupFailed') : 'Account creation failed',
                apiErrorMessage(account, 'An account with this email already exists.')
              );
              return;
            }
          } else {
            Toast.error(
              typeof I18n !== 'undefined' ? I18n.t('messages.signupFailed') : 'Account creation failed',
              apiErrorMessage(account, typeof I18n !== 'undefined' ? I18n.t('messages.signupFailed') : 'Please review your details and try again.')
            );
            return;
          }
        } else {
          AuthSession.save(account.data.data);
          token = account.data.data.token;
        }
      }

      // 2. Submit the business listing (status → PENDING)
      const listingRes = await API.request('/vendors/apply', {
        method: 'POST',
        body: payload,
        auth: true
      });

      if (!listingRes.ok || !listingRes.data?.success) {
        const errMsg = apiErrorMessage(listingRes, 'Please review your business details and try again.');
        Toast.error(
          typeof I18n !== 'undefined' ? (I18n.t('vendorRegister.submitFailed') || 'Listing submission failed') : 'Listing submission failed',
          errMsg
        );
        return;
      }

      // 3. Success — replace the form with the success panel
      form.style.display = 'none';
      const success = document.getElementById('vendor-success');
      if (success) success.style.display = 'block';
      Toast.success(
        typeof I18n !== 'undefined' ? I18n.t('vendorRegister.successTitle') : 'Application submitted!',
        typeof I18n !== 'undefined' ? I18n.t('vendorRegister.successDesc') : 'Your business is now pending review.'
      );
    } catch {
      Toast.error(
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Unable to submit',
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Please check your connection and try again.'
      );
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
