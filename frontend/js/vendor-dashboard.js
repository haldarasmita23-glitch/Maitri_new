/**
 * Maitri — Vendor Business Dashboard (Phase 3 & Phase 4)
 *
 * Dedicated business management portal for authenticated VENDOR accounts.
 * Manages store listing details, customer reviews, complaints resolution,
 * and customer chat inquiries using genuine backend APIs (/api/vendors/me,
 * /api/complaints/vendor/me, /api/reviews/vendor/{id}).
 */

let _currentVendor = null;
let _currentCategoryMap = {};

document.addEventListener('DOMContentLoaded', async () => {
  await initVendorDashboard();
  initTabNavigation();
  bindVendorProfileForm();
  bindVendorAccountForm();
});

// ── Auth Guard & Initialization ─────────────────────────────────

async function initVendorDashboard() {
  const contentEl = document.getElementById('vendor-dashboard-content');
  const gateErrorEl = document.getElementById('vendor-gate-error');
  const titleEl = document.getElementById('gate-error-title');
  const descEl = document.getElementById('gate-error-desc');

  // Verify authentication
  const token = sessionStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN) || localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  const role = AuthSession.getRole();

  if (!token || !role) {
    showGateError(gateErrorEl, contentEl, 'Vendor Access Required', 'Please log in with your vendor account to access your business dashboard.');
    return;
  }

  if (role !== 'VENDOR' && role !== 'ADMIN') {
    showGateError(
      gateErrorEl,
      contentEl,
      'Resident Account Detected',
      'You are currently logged in as a community member. The business portal is reserved for registered local vendors.'
    );
    return;
  }

  // Load categories for mapping and dropdown
  await loadCategories();

  // Load Vendor Profile
  try {
    const result = await API.getMyVendor();
    if (result.ok && result.data?.success && result.data.data) {
      _currentVendor = result.data.data;
      renderVendorHeader(_currentVendor);
      populateProfileForm(_currentVendor);
    } else if (result.status === 404) {
      // Vendor account exists in `users` but application hasn't been submitted
      showNoProfileBanner();
    } else if (result.status === 401) {
      AuthSession.clear();
      window.location.href = 'login.html';
      return;
    }
  } catch (err) {
    console.error('Failed to load vendor profile:', err);
  }

  // Load Account Info (Personal Settings)
  loadAccountSettings();

  // Load Reviews, Complaints, and Chats if vendor profile exists
  if (_currentVendor && _currentVendor.id) {
    loadReviewsData(_currentVendor.id);
    loadComplaintsData();
    loadConversationsData();
  }
}

function showGateError(showEl, hideEl, title, desc) {
  if (showEl) showEl.classList.remove('hidden');
  if (hideEl) hideEl.classList.add('hidden');
  if (title) document.getElementById('gate-error-title').textContent = title;
  if (desc) document.getElementById('gate-error-desc').textContent = desc;
}

// ── Tab Navigation ──────────────────────────────────────────────

function initTabNavigation() {
  const desktopBtns = document.querySelectorAll('.vendor-nav__btn');
  const mobileBtns = document.querySelectorAll('.vendor-mobile-tab-btn');
  const panes = document.querySelectorAll('.vendor-tab-pane');

  function switchTab(tabId) {
    desktopBtns.forEach(btn => btn.classList.toggle('active', btn.dataset.tab === tabId));
    mobileBtns.forEach(btn => btn.classList.toggle('active', btn.dataset.tab === tabId));
    panes.forEach(pane => pane.classList.toggle('active', pane.id === `pane-${tabId}`));
  }

  desktopBtns.forEach(btn => {
    btn.addEventListener('click', () => switchTab(btn.dataset.tab));
  });

  mobileBtns.forEach(btn => {
    btn.addEventListener('click', () => switchTab(btn.dataset.tab));
  });

  document.querySelectorAll('[data-switch-tab]').forEach(btn => {
    btn.addEventListener('click', () => switchTab(btn.dataset.switchTab));
  });

  const quickEditBtn = document.getElementById('btn-quick-edit-profile');
  if (quickEditBtn) {
    quickEditBtn.addEventListener('click', () => switchTab('profile'));
  }
}

// ── Categories Loader ───────────────────────────────────────────

async function loadCategories() {
  try {
    const categories = await Categories.load();
    const select = document.getElementById('vp-category');
    if (!select) return;

    select.innerHTML = '<option value="">Select a category...</option>';
    categories.forEach(cat => {
      _currentCategoryMap[cat.id] = cat;
      _currentCategoryMap[cat.slug] = cat;
      const opt = document.createElement('option');
      opt.value = cat.slug || cat.id;
      opt.textContent = `${cat.icon || '📦'} ${cat.name}`;
      select.appendChild(opt);
    });
  } catch (err) {
    console.error('Failed to load categories:', err);
  }
}

// ── Vendor Header & Status Rendering ────────────────────────────

function renderVendorHeader(vendor) {
  const shopName = vendor.shopName || 'My Business';
  const categoryName = vendor.categoryName || (_currentCategoryMap[vendor.categoryId]?.name) || 'Local Service';
  const area = vendor.area || 'Bangalore';
  const hours = (vendor.openingTime && vendor.closingTime) ? `${vendor.openingTime} - ${vendor.closingTime}` : 'Hours not set';

  document.getElementById('vd-shop-name').textContent = shopName;
  document.getElementById('vd-category').textContent = categoryName;
  document.getElementById('vd-area').textContent = `📍 ${area}`;
  document.getElementById('vd-hours').textContent = `🕒 ${hours}`;
  document.getElementById('vd-avatar').textContent = shopName.charAt(0).toUpperCase();

  // Status badge
  const statusBadge = document.getElementById('vd-status-badge');
  const alertBox = document.getElementById('vendor-status-alert');
  const alertTitle = document.getElementById('status-alert-title');
  const alertDesc = document.getElementById('status-alert-desc');

  if (vendor.status === 'APPROVED') {
    statusBadge.className = 'status-pill status-pill--approved';
    statusBadge.textContent = '🟢 Approved & Live';
    if (alertBox) alertBox.classList.add('hidden');
  } else if (vendor.status === 'REJECTED') {
    statusBadge.className = 'status-pill status-pill--rejected';
    statusBadge.textContent = '🔴 Needs Review';
    if (alertBox) {
      alertBox.className = 'alert alert--danger mb-6';
      alertBox.classList.remove('hidden');
      alertTitle.textContent = 'Application Not Approved';
      alertDesc.textContent = 'Your listing was not approved at this time. Please update your business details or contact support.';
    }
  } else {
    statusBadge.className = 'status-pill status-pill--pending';
    statusBadge.textContent = '⏳ Pending Review';
    if (alertBox) {
      alertBox.className = 'alert alert--warning mb-6';
      alertBox.classList.remove('hidden');
      alertTitle.textContent = 'Application Under Review';
      alertDesc.textContent = 'Your business listing is currently under review by Maitri administrators. You can edit your store details at any time.';
    }
  }

  // Public links
  const publicHref = `vendor-detail.html?id=${encodeURIComponent(vendor.id)}`;
  const publicBtn1 = document.getElementById('btn-view-public');
  const publicBtn2 = document.getElementById('btn-view-public-sidebar');
  if (publicBtn1) publicBtn1.href = publicHref;
  if (publicBtn2) publicBtn2.href = publicHref;

  // KPI Average Rating
  document.getElementById('kpi-avg-rating').textContent = Number(vendor.averageRating || 0).toFixed(1);
}

function showNoProfileBanner() {
  const alertBox = document.getElementById('vendor-status-alert');
  if (alertBox) {
    alertBox.className = 'alert alert--warning mb-6';
    alertBox.classList.remove('hidden');
    document.getElementById('status-alert-title').textContent = 'Complete Your Business Listing';
    document.getElementById('status-alert-desc').textContent = 'Please fill out your Store Profile below to submit your business for review.';
  }
}

// ── Profile Form Population & Submission ────────────────────────

function populateProfileForm(vendor) {
  document.getElementById('vp-shop-name').value = vendor.shopName || '';
  document.getElementById('vp-owner-name').value = vendor.ownerName || '';
  document.getElementById('vp-category').value = vendor.categorySlug || vendor.categoryId || '';
  document.getElementById('vp-phone').value = vendor.phone || '';
  document.getElementById('vp-description').value = vendor.description || '';
  document.getElementById('vp-area').value = vendor.area || 'Peenya';
  document.getElementById('vp-address').value = vendor.address || '';
  document.getElementById('vp-open-time').value = vendor.openingTime || '09:00';
  document.getElementById('vp-close-time').value = vendor.closingTime || '21:00';
  document.getElementById('vp-images').value = (vendor.images || []).join(', ');
}

function bindVendorProfileForm() {
  const form = document.getElementById('vendor-profile-form');
  if (!form) return;

  form.addEventListener('submit', async e => {
    e.preventDefault();
    clearErrors(form);

    const shopName = document.getElementById('vp-shop-name')?.value.trim();
    const ownerName = document.getElementById('vp-owner-name')?.value.trim();
    const category = document.getElementById('vp-category')?.value;
    const phone = document.getElementById('vp-phone')?.value.trim();
    const description = document.getElementById('vp-description')?.value.trim();
    const area = document.getElementById('vp-area')?.value;
    const address = document.getElementById('vp-address')?.value.trim();
    const openingTime = document.getElementById('vp-open-time')?.value;
    const closingTime = document.getElementById('vp-close-time')?.value;
    const imagesRaw = document.getElementById('vp-images')?.value.trim();

    let valid = true;
    if (!shopName || shopName.length < 2) {
      showFieldError('vp-shop-name', 'Shop name must be at least 2 characters.');
      valid = false;
    }
    if (!ownerName || ownerName.length < 2) {
      showFieldError('vp-owner-name', 'Owner name must be at least 2 characters.');
      valid = false;
    }
    if (!category) {
      showFieldError('vp-category', 'Please select a category.');
      valid = false;
    }
    if (!phone || !/^[6-9][0-9]{9}$/.test(phone)) {
      showFieldError('vp-phone', 'Please enter a valid 10-digit mobile number.');
      valid = false;
    }
    if (!description || description.length < 5) {
      showFieldError('vp-description', 'Description must be at least 5 characters.');
      valid = false;
    }
    if (!address) {
      showFieldError('vp-address', 'Address is required.');
      valid = false;
    }
    if (!valid) return;

    const images = imagesRaw ? imagesRaw.split(',').map(s => s.trim()).filter(Boolean) : [];

    const payload = {
      shopName,
      ownerName,
      categoryId: category,
      phone,
      description,
      area,
      address,
      openingTime,
      closingTime,
      images,
    };

    const btn = document.getElementById('btn-save-vendor-profile');
    setLoading(btn, true);
    try {
      let result;
      if (_currentVendor && _currentVendor.id) {
        result = await API.updateMyVendor(payload);
      } else {
        result = await API.applyVendor(payload);
      }

      if (!result.ok || !result.data?.success) {
        Toast.error('Save failed', apiErrorMessage(result, 'Could not update vendor profile.'));
        return;
      }

      _currentVendor = result.data.data;
      renderVendorHeader(_currentVendor);
      Toast.success('Profile saved!', 'Your business details have been updated.');
    } catch {
      Toast.error('Connection error', 'Unable to save business details. Please check connection.');
    } finally {
      setLoading(btn, false);
    }
  });
}

// ── Customer Reviews Loader ─────────────────────────────────────

async function loadReviewsData(vendorId) {
  try {
    const [reviewsRes, summaryRes] = await Promise.all([
      API.getVendorReviews(vendorId, 0, 10),
      API.getVendorRatingSummary(vendorId),
    ]);

    const reviews = (reviewsRes.ok && reviewsRes.data?.data?.content) ? reviewsRes.data.data.content : [];
    const summary = (summaryRes.ok && summaryRes.data?.data) ? summaryRes.data.data : { averageRating: 0, totalReviews: 0, distribution: {} };

    // Update KPI & Nav Badge
    document.getElementById('kpi-total-reviews').textContent = summary.totalReviews || reviews.length;
    document.getElementById('nav-review-count').textContent = summary.totalReviews || reviews.length;
    document.getElementById('reviews-avg-display').textContent = Number(summary.averageRating || 0).toFixed(1);
    document.getElementById('reviews-stars-display').textContent = '⭐'.repeat(Math.round(summary.averageRating || 0)) || '⭐⭐⭐⭐⭐';
    document.getElementById('reviews-total-display').textContent = `Based on ${summary.totalReviews || reviews.length} customer reviews`;

    // Render distribution bars
    renderRatingBars(summary.distribution || {}, summary.totalReviews || reviews.length);

    // Render reviews list
    renderReviewsList(reviews);
  } catch (err) {
    console.error('Failed to load reviews:', err);
  }
}

function renderRatingBars(dist, total) {
  const container = document.getElementById('reviews-distribution-bars');
  if (!container) return;

  container.innerHTML = '';
  for (let star = 5; star >= 1; star--) {
    const count = dist[star] || dist[String(star)] || 0;
    const pct = total > 0 ? Math.round((count / total) * 100) : 0;

    const row = document.createElement('div');
    row.className = 'rating-bar-row';
    row.innerHTML = `
      <span style="width: 45px;">${star} ⭐</span>
      <div class="rating-bar-progress">
        <div class="rating-bar-fill" style="width: ${pct}%;"></div>
      </div>
      <span style="width: 35px; text-align: right;">${count}</span>
    `;
    container.appendChild(row);
  }
}

function renderReviewsList(reviews) {
  const container = document.getElementById('vendor-reviews-container');
  const previewContainer = document.getElementById('overview-recent-reviews');
  if (!container) return;

  if (reviews.length === 0) {
    const emptyHtml = '<p style="color: var(--color-text-muted); font-size: var(--font-size-sm); text-align: center; padding: var(--space-6);">No customer reviews yet. Once local residents review your business, they will appear here.</p>';
    container.innerHTML = emptyHtml;
    if (previewContainer) previewContainer.innerHTML = emptyHtml;
    return;
  }

  const listHtml = reviews.map(r => `
    <div class="vendor-review-item">
      <div class="vendor-review__top">
        <span class="vendor-review__user">${escapeHtml(r.userName || 'Community Resident')}</span>
        <span class="vendor-review__stars">${'⭐'.repeat(r.rating || 5)}</span>
      </div>
      <p class="vendor-review__comment">${escapeHtml(r.comment || 'No comment provided.')}</p>
      <span class="vendor-review__date">${r.createdAt ? new Date(r.createdAt).toLocaleDateString() : ''}</span>
    </div>
  `).join('');

  container.innerHTML = listHtml;
  if (previewContainer) {
    previewContainer.innerHTML = reviews.slice(0, 3).map(r => `
      <div class="vendor-review-item" style="padding: var(--space-2) 0;">
        <div class="vendor-review__top">
          <span class="vendor-review__user">${escapeHtml(r.userName || 'Resident')}</span>
          <span class="vendor-review__stars">${'⭐'.repeat(r.rating || 5)}</span>
        </div>
        <p class="vendor-review__comment" style="font-size: var(--font-size-xs);">${escapeHtml(r.comment || '')}</p>
      </div>
    `).join('');
  }
}

// ── Complaints Loader & Status Updates ───────────────────────────

async function loadComplaintsData() {
  try {
    const result = await API.getVendorComplaints();
    const complaints = (result.ok && result.data?.data) ? result.data.data : [];

    const activeCount = complaints.filter(c => c.status !== 'RESOLVED').length;
    document.getElementById('kpi-active-complaints').textContent = activeCount;
    document.getElementById('nav-complaint-count').textContent = activeCount;

    renderComplaintsList(complaints);
  } catch (err) {
    console.error('Failed to load vendor complaints:', err);
  }
}

function renderComplaintsList(complaints) {
  const container = document.getElementById('vendor-complaints-container');
  const previewContainer = document.getElementById('overview-recent-complaints');
  if (!container) return;

  if (complaints.length === 0) {
    const emptyHtml = '<p style="color: var(--color-text-muted); font-size: var(--font-size-sm); text-align: center; padding: var(--space-6);">🎉 No complaints lodged against your business.</p>';
    container.innerHTML = emptyHtml;
    if (previewContainer) previewContainer.innerHTML = emptyHtml;
    return;
  }

  const listHtml = complaints.map(c => {
    let statusClass = 'badge--warning';
    if (c.status === 'RESOLVED') statusClass = 'badge--success';
    if (c.status === 'IN_PROGRESS') statusClass = 'badge--info';

    let actionBtn = '';
    if (c.status === 'PENDING') {
      actionBtn = `<button type="button" class="btn btn--outline btn--xs" onclick="updateComplaint('${c.id}', 'IN_PROGRESS')">Start Handling</button>`;
    } else if (c.status === 'IN_PROGRESS') {
      actionBtn = `<button type="button" class="btn btn--primary btn--xs" onclick="updateComplaint('${c.id}', 'RESOLVED')">Mark Resolved</button>`;
    }

    return `
      <div class="vendor-complaint-card">
        <div class="vendor-complaint__info">
          <div class="flex items-center gap-2">
            <span class="vendor-complaint__type">⚠️ ${escapeHtml(c.complaintType || 'Complaint')}</span>
            <span class="badge ${statusClass}">${c.status}</span>
          </div>
          <p class="vendor-complaint__desc">${escapeHtml(c.description || '')}</p>
          <span style="font-size: var(--font-size-2xs); color: var(--color-text-muted);">Filed on ${c.createdAt ? new Date(c.createdAt).toLocaleDateString() : 'recent'}</span>
        </div>
        <div class="vendor-complaint__actions">
          ${actionBtn}
        </div>
      </div>
    `;
  }).join('');

  container.innerHTML = listHtml;

  if (previewContainer) {
    previewContainer.innerHTML = complaints.slice(0, 3).map(c => `
      <div class="flex justify-between items-center py-2" style="border-bottom: 1px solid var(--color-border);">
        <div>
          <strong style="font-size: var(--font-size-xs);">${escapeHtml(c.complaintType)}</strong>
          <div style="font-size: var(--font-size-2xs); color: var(--color-text-muted);">${c.status}</div>
        </div>
        ${c.status !== 'RESOLVED' ? `<span class="badge badge--warning">Active</span>` : `<span class="badge badge--success">Resolved</span>`}
      </div>
    `).join('');
  }
}

window.updateComplaint = async function(id, targetStatus) {
  try {
    const result = await API.updateComplaintStatus(id, targetStatus);
    if (!result.ok || !result.data?.success) {
      Toast.error('Update failed', apiErrorMessage(result, 'Could not update complaint status.'));
      return;
    }
    Toast.success('Status updated', `Complaint marked as ${targetStatus}.`);
    loadComplaintsData();
  } catch {
    Toast.error('Error', 'Unable to update complaint status.');
  }
};

// ── Customer Conversations Loader ───────────────────────────────

async function loadConversationsData() {
  try {
    const result = await API.getChats();
    // API.getChats() returns the parsed JSON directly (not a Response wrapper)
    // so chats live in result.data when success is true
    const chats = (result && result.success && Array.isArray(result.data))
      ? result.data
      : [];

    document.getElementById('kpi-conversations').textContent = chats.length;

    const container = document.getElementById('vendor-chats-container');
    if (!container) return;

    if (chats.length === 0) {
      container.innerHTML = '<p style="color: var(--color-text-muted); font-size: var(--font-size-sm); text-align: center; padding: var(--space-6);">No active customer chats yet. When customers click &ldquo;Message Vendor&rdquo;, conversations will appear here.</p>';
      return;
    }

    container.innerHTML = chats.map(chat => {
      // chat.otherPartyId is the partner's user account ID — this is what the
      // chat-detail page expects as the ?chatId= parameter (NOT chat.id which is
      // the MongoDB document ID of the last message).
      const partnerId   = chat.otherPartyId || '';
      const partnerName = chat.otherPartyName || 'Customer';
      const partnerRole = chat.otherPartyRole || 'USER';

      const chatUrl = `chat-detail.html?chatId=${encodeURIComponent(partnerId)}&name=${encodeURIComponent(partnerName)}&role=${encodeURIComponent(partnerRole)}`;

      // Translation indicator: message was translated for this vendor
      const isTranslated = !chat.lastMessageIsOwn && chat.translationStatus === 'TRANSLATED';
      const transTag = isTranslated
        ? '<span style="font-size:0.7rem; margin-left:4px;" title="Auto-translated">\uD83C\uDF10</span>'
        : '';

      // Unread badge
      const unreadBadge = (chat.unreadCount && chat.unreadCount > 0)
        ? `<span style="background: var(--gradient-warm); color:#fff; font-size:0.7rem; font-weight:700; padding:1px 7px; border-radius:999px; margin-left:auto;">${chat.unreadCount}</span>`
        : '';

      return `
        <div class="card p-4 mb-3" style="display:flex; justify-content:space-between; align-items:center; gap: var(--space-4);">
          <div style="flex:1; min-width:0;">
            <div style="display:flex; align-items:center; gap:var(--space-2); margin-bottom:var(--space-1);">
              <strong style="color: var(--color-text-primary); font-size: var(--font-size-sm);">\uD83D\uDCAC ${escapeHtml(partnerName)}</strong>
              ${unreadBadge}
            </div>
            <p style="font-size: var(--font-size-xs); color: var(--color-text-secondary); margin:0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">
              ${escapeHtml(chat.lastMessage || 'Open conversation')}${transTag}
            </p>
          </div>
          <a href="${chatUrl}" class="btn btn--outline btn--sm" style="flex-shrink:0;">Reply \u2192</a>
        </div>
      `;
    }).join('');
  } catch (err) {
    console.error('Failed to load chats:', err);
  }
}

// ── Account Settings (Personal User Data) ────────────────────────

async function loadAccountSettings() {
  const user = AuthSession.user();
  if (user) {
    const nameInput = document.getElementById('va-name');
    const emailInput = document.getElementById('va-email');
    const langSelect = document.getElementById('va-language');
    const photoInput = document.getElementById('va-photo');

    if (nameInput) nameInput.value = user.name || '';
    if (emailInput) emailInput.value = user.email || '';
    if (langSelect) langSelect.value = user.preferredLanguage || 'en';
    if (photoInput) photoInput.value = user.profilePhoto || '';
  }

  try {
    const result = await API.getUserPreferences();
    if (result.ok && result.data?.success && result.data.data) {
      const prefs = result.data.data;
      const langSelect = document.getElementById('va-language');
      if (langSelect && prefs.preferredLanguage) {
        langSelect.value = prefs.preferredLanguage;
      }
    }
  } catch (err) {
    console.error('Failed to load user preferences:', err);
  }
}

function bindVendorAccountForm() {
  const form = document.getElementById('vendor-account-form');
  if (!form) return;

  form.addEventListener('submit', async e => {
    e.preventDefault();
    clearErrors(form);

    const name = document.getElementById('va-name')?.value.trim();
    const language = document.getElementById('va-language')?.value;
    const photo = document.getElementById('va-photo')?.value.trim();

    if (!name || name.length < 2) {
      showFieldError('va-name', 'Full name must be at least 2 characters.');
      return;
    }

    const btn = document.getElementById('btn-save-vendor-account');
    setLoading(btn, true);
    try {
      const langRes = await API.updateLanguagePreference(language);
      if (langRes.ok && langRes.data?.success) {
        const user = AuthSession.user() || {};
        user.name = name;
        user.preferredLanguage = language;
        if (photo) user.profilePhoto = photo;
        AuthSession.save({ token: AuthSession.token(), user });
        if (typeof I18n !== 'undefined' && language) {
          I18n.setLanguage(language, false);
        }
        Toast.success('Account updated!', 'Your personal account settings have been saved.');
      } else {
        Toast.error('Save failed', apiErrorMessage(langRes, 'Could not save language preference.'));
      }
    } catch {
      Toast.error('Connection error', 'Unable to save settings.');
    } finally {
      setLoading(btn, false);
    }
  });
}

// Reactive language change listener for vendor dashboard
window.addEventListener('maitri:language-change', () => {
  if (_currentVendor) {
    renderVendorHeader(_currentVendor);
    populateProfileForm(_currentVendor);
  }
});

