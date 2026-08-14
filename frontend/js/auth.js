/**
 * Maitri — Auth Pages JavaScript
 *
 * Phase 2: Client-side form validation and UI feedback only.
 * No actual API calls — Phase 3 will wire these up.
 */

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

    // Phase 2: show "coming soon" notice
    const btn = form.querySelector('button[type="submit"]');
    setLoading(btn, true);

    await delay(800); // simulate network

    setLoading(btn, false);
    Toast.info('Authentication coming soon!',
      'Login will be available in Phase 3. For now, browse vendors without logging in.');
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
    const phone    = form.querySelector('#reg-phone')?.value.trim();
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
    if (phone && !isValidPhone(phone)) {
      showFieldError('reg-phone', 'Enter a valid 10-digit mobile number.');
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
    await delay(1000);
    setLoading(btn, false);

    Toast.info('Registration coming soon!',
      'User accounts will be available in Phase 3.');
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

    const btn = form.querySelector('button[type="submit"]');
    setLoading(btn, true);
    await delay(1200);
    setLoading(btn, false);

    Toast.info('Vendor registration coming soon!',
      'Vendors will be able to register in Phase 3. Your details have been noted.');
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
      tabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      // Role-specific behaviour can be added in Phase 3
    });
  });
}


// ── Utility Helpers ────────────────────────────────────────────

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function isValidPhone(phone) {
  return /^[6-9]\d{9}$/.test(phone.replace(/\s+/g, ''));
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

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}
