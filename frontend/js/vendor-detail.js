/**
 * Maitri — Vendor Detail Page JavaScript (Phase 7: Reviews Integration)
 */

let _cachedVendor = null;

document.addEventListener('DOMContentLoaded', async () => {
  // ── DIAGNOSTIC LOGGING (temporary) ──────────────────────────────
  console.log('[VendorDetail] URL:', window.location.href);
  console.log('[VendorDetail] search:', window.location.search);
  console.log('[VendorDetail] hash:', window.location.hash);
  // ────────────────────────────────────────────────────────────────

  const params = new URLSearchParams(window.location.search);
  let vendorId = params.get('id') || params.get('vendorId') || params.get('v');

  console.log('[VendorDetail] vendorId from params:', vendorId);

  // Fallback: Check hash if query string was placed after hash or as hash anchor
  if (!vendorId && window.location.hash) {
    const hash = window.location.hash.replace(/^#\/?/, '');
    if (hash.includes('=')) {
      const hashParams = new URLSearchParams(hash.includes('?') ? hash.split('?')[1] : hash);
      vendorId = hashParams.get('id') || hashParams.get('vendorId') || hashParams.get('v');
    } else if (hash && !hash.includes('/')) {
      vendorId = hash;
    }
    console.log('[VendorDetail] vendorId from hash fallback:', vendorId);
  }

  if (!vendorId) {
    console.error('[VendorDetail] ERROR: No vendor ID in URL. Full URL was:', window.location.href);
    showError(typeof I18n !== 'undefined' ? I18n.t('vendorDetail.noIdSpecified') : 'No vendor ID specified.');
    return;
  }

  const vendor = await Vendors.getById(vendorId);
  console.log('[VendorDetail] Vendors.getById result:', vendor ? 'FOUND: ' + vendor.shopName : 'NULL (not found)');
  if (!vendor) {
    showError(typeof I18n !== 'undefined' ? I18n.t('vendorDetail.notFound') : 'Vendor not found.');
    return;
  }

  _cachedVendor = vendor;

  try {
    console.log('[VendorDetail] Calling renderVendorDetail...');
    renderVendorDetail(vendor);
    console.log('[VendorDetail] renderVendorDetail completed OK');
  } catch (err) {
    console.error('[VendorDetail] renderVendorDetail THREW:', err);
    showError('Rendering error: ' + err.message);
    return;
  }

  try {
    initTabs();
    initRatingInput();
    await Favourites.load();
    initFavButton(vendor.id);
    initMessageVendorButton(vendor);

    const currentUser = getCurrentUser();
    const myReview = await renderReviews(vendor.id, currentUser);
    await initReviewForm(vendor.id, currentUser, myReview);

    // Phase 9 — Raise a Complaint (USER-only functionality)
    const complaintContainer = document.getElementById('complaint-form-container');
    if (complaintContainer) {
      await initComplaintForm(vendor.id, complaintContainer);
    }
  } catch (err) {
    console.error('[VendorDetail] Post-render init THREW:', err);
  }

  window.addEventListener('maitri:language-change', async () => {
    if (_cachedVendor) {
      renderVendorDetail(_cachedVendor);
      initFavButton(_cachedVendor.id);
      initMessageVendorButton(_cachedVendor);
      await renderReviews(_cachedVendor.id, getCurrentUser());
    }
  });
});


function renderVendorDetail(v) {
  // Page title
  document.title = `${v.shopName} — Maitri`;

  // Gallery
  const gallery = document.getElementById('vendor-gallery');
  if (gallery) {
    gallery.innerHTML = `
      <div class="vendor-gallery__placeholder" aria-hidden="true">${v.emoji}</div>
      <div class="vendor-gallery__overlay"></div>
    `;
  }

  // Breadcrumb
  const crumb = document.getElementById('vendor-breadcrumb-name');
  if (crumb) crumb.textContent = v.shopName;

  // Name
  const nameEl = document.getElementById('vendor-name');
  if (nameEl) nameEl.textContent = v.shopName;

  // Category badge
  const catEl = document.getElementById('vendor-category-badge');
  if (catEl) {
    const localizedCat = typeof I18n !== 'undefined' ? I18n.translateCategory(v.categoryName) : v.categoryName;
    catEl.innerHTML = `<span class="badge badge--primary">${v.emoji} ${escapeHtml(localizedCat)}</span>`;
  }

  // Rating
  const ratingEl = document.getElementById('vendor-rating');
  if (ratingEl) {
    const reviewWord = typeof I18n !== 'undefined' ? I18n.t('common.reviews') : 'reviews';
    ratingEl.innerHTML = `
      <span class="rating">
        ${renderStars(v.averageRating, 'lg')}
        <span class="rating__value font-bold">${v.averageRating}</span>
        <span class="rating__count">(${v.reviewCount} ${reviewWord})</span>
      </span>
    `;
  }

  // Open status
  const statusEl = document.getElementById('vendor-status');
  if (statusEl) {
    const open = isVendorOpen(v.openingTime, v.closingTime);
    const openText = open
      ? (typeof I18n !== 'undefined' ? I18n.t('common.openNow') : 'Open Now')
      : (typeof I18n !== 'undefined' ? I18n.t('common.closed') : 'Closed');
    statusEl.innerHTML = `
      <span class="status-dot ${open ? 'status-dot--green status-dot--pulse' : 'status-dot--red'}"></span>
      <span class="sidebar-status__value ${open ? 'open' : 'closed'}">${openText}</span>
    `;
  }

  // Hours
  const hoursEl = document.getElementById('vendor-hours');
  if (hoursEl) hoursEl.textContent = `${formatTime(v.openingTime)} – ${formatTime(v.closingTime)}`;

  // Description
  const descEl = document.getElementById('vendor-description');
  if (descEl) descEl.textContent = v.description;

  // Address info item
  const addrEl = document.getElementById('vendor-address');
  if (addrEl) addrEl.textContent = v.address;

  // Phone
  const phoneEl = document.getElementById('vendor-phone');
  if (phoneEl) phoneEl.textContent = v.phone;

  // Call button — wire the tel: href directly so it works for live API data
  const phoneLink = document.getElementById('sidebar-phone-link');
  if (phoneLink) phoneLink.href = 'tel:' + v.phone.replace(/ /g, '');

  // Tags
  const tagsEl = document.getElementById('vendor-tags');
  if (tagsEl) {
    tagsEl.innerHTML = v.tags.map(t =>
      `<span class="badge badge--gray">${escapeHtml(t)}</span>`
    ).join('');
  }

  // Owner
  const ownerEl = document.getElementById('vendor-owner');
  if (ownerEl) ownerEl.textContent = v.ownerName;

  // Sidebar vendor name
  const sidebarName = document.getElementById('sidebar-vendor-name');
  if (sidebarName) sidebarName.textContent = v.shopName;

  // Sidebar location/hours (set directly — the detail render is now async)
  const sAddr = document.getElementById('sidebar-address');
  if (sAddr) sAddr.textContent = v.address;
  const sHrs = document.getElementById('sidebar-hours');
  if (sHrs) sHrs.textContent = `${formatTime(v.openingTime)} – ${formatTime(v.closingTime)}`;
}


async function renderReviews(vendorId, currentUser) {
  try {
    // Get rating summary
    const summaryResult = await API.getVendorRatingSummary(vendorId);
    const summary = summaryResult.success ? summaryResult.data : { averageRating: 0, totalReviews: 0, ratingDistribution: {} };

    // Get reviews list (first page)
    const reviewsResult = await API.getVendorReviews(vendorId, 0, 10);
    const reviews = reviewsResult.success ? reviewsResult.data.content : [];

    // Update summary display
    const bigScore = document.getElementById('reviews-big-score');
    if (bigScore) bigScore.textContent = Number(summary.averageRating || 0).toFixed(1);

    const summaryStars = document.getElementById('reviews-summary-stars');
    if (summaryStars) summaryStars.innerHTML = renderStars(summary.averageRating || 0, 'lg');

    const summaryCount = document.getElementById('reviews-summary-count');
    if (summaryCount) summaryCount.textContent = `${summary.totalReviews || 0} reviews`;

    // Rating bars
    const barsEl = document.getElementById('rating-bars');
    if (barsEl) {
      const dist = summary.ratingDistribution || {};
      const total = summary.totalReviews || 1;
      barsEl.innerHTML = [5, 4, 3, 2, 1].map(star => {
        const count = dist[star] || 0;
        const pct = Math.round((count / total) * 100);
        return `
          <div class="rating-bar">
            <span class="rating-bar__label">${star}★</span>
            <div class="rating-bar__track">
              <div class="rating-bar__fill" style="width: ${pct}%"></div>
            </div>
            <span class="rating-bar__count">${count}</span>
          </div>
        `;
      }).join('');
    }

    // Review cards
    const listEl = document.getElementById('reviews-list');
    if (!listEl) return null;

    if (reviews.length === 0) {
      listEl.innerHTML = `
        <div class="empty-state">
          <div class="empty-state__icon">⭐</div>
          <h3>No reviews yet</h3>
          <p>Be the first to review this vendor!</p>
        </div>
      `;
      return null;
    }

    const myUserId = currentUser ? currentUser.id : null;
    const myReview = reviews.find(r => r.userId && r.userId === myUserId) || null;

    listEl.innerHTML = reviews.map(r => {
      const isMine = myReview && r.id === myReview.id;
      const actions = isMine ? `
        <div class="review-card__actions">
          <button type="button" class="btn btn--ghost btn--sm" data-review-action="edit" data-review-id="${r.id}">✏️ Edit</button>
          <button type="button" class="btn btn--ghost btn--sm btn--danger-text" data-review-action="delete" data-review-id="${r.id}">🗑️ Delete</button>
        </div>
      ` : '';
      return `
        <div class="review-card">
          <div class="review-card__header">
            <div class="review-card__user">
              <div class="review-card__avatar" aria-hidden="true">${(r.userName || '?').charAt(0).toUpperCase()}</div>
              <div>
                <div class="review-card__name">${r.userName || 'Unknown User'}</div>
                <div class="review-card__date">${formatDate(r.createdAt)}</div>
              </div>
            </div>
            <span class="rating">
              ${renderStars(r.rating, 'sm')}
            </span>
          </div>
          <p class="review-card__text">${r.reviewText || ''}</p>
          ${actions}
        </div>
      `;
    }).join('');

    // Edit/delete handlers for the current user's own reviews (idempotent — `onclick` replaces any prior handler)
    listEl.onclick = (event) => {
      const btn = event.target.closest('[data-review-action]');
      if (!btn) return;
      const reviewId = btn.dataset.reviewId;
      const review = reviews.find(r => r.id === reviewId);
      if (!review) return;

      if (btn.dataset.reviewAction === 'edit') {
        openReviewEdit(review);
      } else if (btn.dataset.reviewAction === 'delete') {
        deleteOwnReview(review, vendorId);
      }
    };

    return myReview;
  } catch (error) {
    console.error('Failed to load reviews:', error);
    const listEl = document.getElementById('reviews-list');
    if (listEl) {
      listEl.innerHTML = `
        <div class="alert alert--error">
          <span class="alert__icon">⚠️</span>
          <div class="alert__text">
            <strong>Error loading reviews</strong><br>
            Please try again later.
          </div>
        </div>
      `;
    }
    return null;
  }
}


function initTabs() {
  const tabs = document.querySelectorAll('.detail-tab');
  const panels = document.querySelectorAll('.tab-panel');

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => { t.classList.remove('active'); t.setAttribute('aria-selected', 'false'); });
      panels.forEach(p => p.classList.remove('active'));

      tab.classList.add('active');
      tab.setAttribute('aria-selected', 'true');
      const panelId = tab.dataset.tab;
      document.getElementById(panelId)?.classList.add('active');
    });
  });
}


function initRatingInput() {
  const stars = document.querySelectorAll('.rating-input__star');
  const input = document.getElementById('rating-value');
  if (!stars.length) return;

  stars.forEach(star => {
    star.addEventListener('click', () => {
      const val = parseInt(star.dataset.value, 10);
      if (input) input.value = val;
      stars.forEach(s => {
        s.classList.toggle('active', parseInt(s.dataset.value, 10) <= val);
      });
    });

    star.addEventListener('mouseenter', () => {
      const val = parseInt(star.dataset.value, 10);
      stars.forEach(s => {
        s.style.color = parseInt(s.dataset.value, 10) <= val ? 'var(--color-star-filled)' : '';
      });
    });

    star.addEventListener('mouseleave', () => {
      stars.forEach(s => { s.style.color = ''; });
    });
  });
}


/** Returns the locally stored user (or null). Safe when components.js is absent. */
function getCurrentUser() {
  try {
    return (typeof AuthSession !== 'undefined' && AuthSession.user())
      || (typeof Navbar !== 'undefined' && Navbar.storedUser())
      || JSON.parse(sessionStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA) || localStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA))
      || null;
  } catch {
    return null;
  }
}

/** The review form's live mode. 'create' submits a new review; 'edit' updates an existing one. */
let reviewFormState = { mode: 'create', reviewId: null };

/** Switches the review form between create and edit mode, optionally pre-filling values. */
function setReviewFormMode(mode, review = null) {
  const form = document.getElementById('review-form');
  if (!form) return;

  reviewFormState = { mode, reviewId: review ? review.id : null };

  const titleEl = document.querySelector('.write-review h4');
  if (titleEl) titleEl.textContent = mode === 'edit' ? 'Your Review' : 'Write a Review';

  const btn = form.querySelector('button[type="submit"]');
  if (btn) btn.textContent = mode === 'edit' ? 'Update Review' : 'Submit Review';

  if (review) {
    const input = document.getElementById('rating-value');
    if (input) input.value = review.rating;
    const text = document.getElementById('review-text');
    if (text) text.value = review.reviewText || '';
    document.querySelectorAll('.rating-input__star').forEach(s => {
      s.classList.toggle('active', parseInt(s.dataset.value, 10) <= review.rating);
    });
  }
}

/** Opens the form in edit mode pre-filled with the given review. */
function openReviewEdit(review) {
  setReviewFormMode('edit', review);
  const form = document.getElementById('review-form');
  if (form) form.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

/** Deletes the current user's own review after confirmation, then refreshes. */
async function deleteOwnReview(review, vendorId) {
  const confirmMsg = typeof I18n !== 'undefined' ? I18n.t('vendorDetail.confirmDeleteReview') : 'Delete your review? This cannot be undone.';
  if (!window.confirm(confirmMsg)) return;

  try {
    const result = await API.deleteReview(review.id);
    if (result.success) {
      Toast.success(typeof I18n !== 'undefined' ? I18n.t('messages.reviewDeleted') : 'Review deleted.', '');
      setReviewFormMode('create');
      const currentUser = getCurrentUser();
      await renderReviews(vendorId, currentUser);
    } else {
      Toast.error(typeof I18n !== 'undefined' ? I18n.t('messages.unknownError') : 'Could not delete review', result.message || 'Please try again.');
    }
  } catch {
    Toast.error(
      typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Network error',
      typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Please check your connection and try again.'
    );
  }
}

async function initReviewForm(vendorId, currentUser, myReview) {
  const form = document.getElementById('review-form');
  if (!form) return;

  const token = sessionStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN) || localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);

  // Logged out — ask the visitor to log in
  if (!token || !currentUser) {
    const writeReviewEl = document.querySelector('.write-review');
    if (writeReviewEl) {
      const title = typeof I18n !== 'undefined' ? I18n.t('vendorDetail.writeReviewTitle') : 'Write a Review';
      const logReq = typeof I18n !== 'undefined' ? I18n.t('profile.loginRequired') : 'Log in required';
      const logPrompt = typeof I18n !== 'undefined' ? I18n.t('vendorDetail.loginToReview', { loginUrl: 'login.html' }) : 'Please <a href="login.html">log in</a> to write a review for this vendor.';
      writeReviewEl.innerHTML = `
        <h4>${escapeHtml(title)}</h4>
        <div class="alert alert--info" role="note">
          <span class="alert__icon">🔐</span>
          <div class="alert__text">
            <strong>${escapeHtml(logReq)}</strong><br>
            ${logPrompt}
          </div>
        </div>
      `;
    }
    return;
  }

  // Vendors cannot submit reviews (maintains authenticity)
  if (currentUser.role === 'VENDOR') {
    const writeReviewEl = document.querySelector('.write-review');
    if (writeReviewEl) {
      const title = typeof I18n !== 'undefined' ? I18n.t('vendorDetail.writeReviewTitle') : 'Write a Review';
      const blocked = typeof I18n !== 'undefined' ? I18n.t('vendorDetail.vendorComplaintBlocked') : 'Business accounts cannot write reviews';
      writeReviewEl.innerHTML = `
        <h4>${escapeHtml(title)}</h4>
        <div class="alert alert--warning" role="note">
          <span class="alert__icon">⚠️</span>
          <div class="alert__text">
            <strong>${escapeHtml(blocked)}</strong><br>
            ${escapeHtml(blocked)}
          </div>
        </div>
      `;
    }
    return;
  }

  // Users who already reviewed this vendor land in edit mode, pre-filled
  if (myReview) {
    setReviewFormMode('edit', myReview);
  }

  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const submitBtn = form.querySelector('button[type="submit"]');
    const originalText = submitBtn.textContent;
    submitBtn.disabled = true;
    submitBtn.textContent = typeof I18n !== 'undefined' ? I18n.t('common.loading') : 'Saving...';

    try {
      const rating = parseInt(document.getElementById('rating-value')?.value || '0', 10);
      const reviewText = document.getElementById('review-text')?.value.trim();

      if (!rating) {
        Toast.warning(
          typeof I18n !== 'undefined' ? I18n.t('validation.ratingRequired') : 'Rating required',
          typeof I18n !== 'undefined' ? I18n.t('validation.ratingRequired') : 'Please select a star rating.'
        );
        return;
      }
      if (reviewText && reviewText.length < 10) {
        Toast.warning(
          typeof I18n !== 'undefined' ? I18n.t('validation.reviewTextRequired') : 'Review too short',
          typeof I18n !== 'undefined' ? I18n.t('validation.reviewTextRequired') : 'Please write at least 10 characters, or leave it empty.'
        );
        return;
      }

      let result;
      if (reviewFormState.mode === 'edit' && reviewFormState.reviewId) {
        result = await API.updateReview(reviewFormState.reviewId, { rating, reviewText: reviewText || null });
      } else {
        result = await API.submitReview({ vendorId, rating, reviewText: reviewText || null });
      }

      if (result.success) {
        Toast.success(
          reviewFormState.mode === 'edit'
            ? (typeof I18n !== 'undefined' ? I18n.t('messages.reviewUpdated') : 'Review updated!')
            : (typeof I18n !== 'undefined' ? I18n.t('messages.reviewSubmitted') : 'Review submitted!'),
          typeof I18n !== 'undefined' ? I18n.t('common.save') : 'Thank you for your feedback.'
        );

        // Refresh the list + summary, then keep the form in edit mode with the saved values
        const updated = result.data?.data || null;
        const refreshed = await renderReviews(vendorId, getCurrentUser());
        setReviewFormMode('edit', updated || refreshed || null);
      } else {
        if (result.message && result.message.includes('already reviewed')) {
          Toast.warning(
            typeof I18n !== 'undefined' ? I18n.t('messages.unknownError') : 'Already reviewed',
            result.message
          );
          const refreshed = await renderReviews(vendorId, getCurrentUser());
          if (refreshed) setReviewFormMode('edit', refreshed);
        } else {
          Toast.error(typeof I18n !== 'undefined' ? I18n.t('messages.unknownError') : 'Submission failed', result.message || 'Please try again.');
        }
      }
    } catch (error) {
      console.error('Failed to submit review:', error);
      Toast.error(
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Network error',
        typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Please check your connection and try again.'
      );
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = originalText;
    }
  });
}


function initFavButton(vendorId) {
  const btn = document.getElementById('fav-btn');
  if (!btn) return;

  const updateBtn = () => {
    const isFav = Favourites.has(vendorId);
    btn.textContent = isFav ? '❤️ Saved' : '🤍 Save';
    btn.classList.toggle('btn--accent', isFav);
    btn.classList.toggle('btn--outline', !isFav);
  };

  updateBtn();

  btn.addEventListener('click', async () => {
    const added = await Favourites.toggle(vendorId);
    if (added === null) return; // login / blocked prompt already shown
    updateBtn();
    Toast[added ? 'success' : 'info'](
      added ? 'Saved to favourites!' : 'Removed from favourites',
    );
  });
}


/**
 * Initialise the "Message Vendor" button(s).
 * Only shown for authenticated USER users (VENDOR users cannot message
 * themselves, and USER↔USER messaging is blocked by the backend).
 *
 * @param {object} vendor - The normalized vendor object (includes userId)
 */
function initMessageVendorButton(vendor) {
  const mainBtn = document.getElementById('message-vendor-btn');
  const sidebarBtn = document.getElementById('sidebar-message-vendor-btn');
  const buttons = [mainBtn, sidebarBtn].filter(Boolean);

  if (buttons.length === 0) return;

  const currentUser = getCurrentUser();

  // If the logged-in user is the owner of this vendor, hide messaging self
  if (currentUser && currentUser.id && vendor.userId && currentUser.id === vendor.userId) {
    buttons.forEach(btn => { btn.style.display = 'none'; });
    return;
  }

  const targetRecipientId = vendor.userId || vendor.id;

  buttons.forEach(btn => {
    btn.style.display = 'inline-flex';
    const label = typeof I18n !== 'undefined' ? I18n.t('vendorDetail.message') : 'Message Vendor';
    btn.innerHTML = `💬 <span data-i18n="vendorDetail.message">${escapeHtml(label)}</span>`;

    btn.onclick = async (e) => {
      e.preventDefault();

      const token = sessionStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN) || localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
      const user = getCurrentUser();

      // Guard: must be authenticated
      if (!token || !user) {
        const loginReqTitle = typeof I18n !== 'undefined' ? I18n.t('profile.loginRequired') : 'Login required';
        const loginReqText = typeof I18n !== 'undefined' ? I18n.t('chat.loginRequired') : 'Please log in to start a conversation.';
        Toast.warning(loginReqTitle, loginReqText.replace(/<[^>]*>?/gm, ''));
        const returnUrl = encodeURIComponent(window.location.pathname + window.location.search);
        window.location.href = `login.html?redirect=${returnUrl}`;
        return;
      }

      // VENDOR users cannot message other vendors
      if (user.role === 'VENDOR') {
        Toast.warning(
          typeof I18n !== 'undefined' ? I18n.t('common.notAllowed') : 'Not Allowed',
          typeof I18n !== 'undefined' ? I18n.t('vendorDetail.vendorMessageBlocked') : 'Vendor accounts can only message regular members.'
        );
        return;
      }

      buttons.forEach(b => {
        b.disabled = true;
        b.textContent = typeof I18n !== 'undefined' ? I18n.t('vendorDetail.startingConversation') : 'Starting…';
      });

      try {
        const result = await API.startConversation(targetRecipientId, 'VENDOR');
        if (!result || (result.success === false && result.message)) {
          Toast.error(
            typeof I18n !== 'undefined' ? I18n.t('vendorDetail.couldNotStart') : 'Could not start chat',
            result?.message || 'Please try again.'
          );
          return;
        }

        // Navigate to the conversation detail page
        const params = new URLSearchParams({
          chatId: targetRecipientId,
          name: vendor.shopName || '',
          role: 'VENDOR',
        });
        window.location.href = `chat-detail.html?${params.toString()}`;
      } catch (err) {
        console.error('[Chat] Error starting conversation:', err);
        Toast.error(
          typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Network error',
          typeof I18n !== 'undefined' ? I18n.t('messages.connectionError') : 'Please check your connection and try again.'
        );
      } finally {
        buttons.forEach(b => {
          b.disabled = false;
          const msgLabel = typeof I18n !== 'undefined' ? I18n.t('vendorDetail.message') : 'Message Vendor';
          b.innerHTML = `💬 <span data-i18n="vendorDetail.message">${escapeHtml(msgLabel)}</span>`;
        });
      }
    };
  });
}


function showLoading() {
  const main = document.getElementById('vendor-detail-main');
  if (main) {
    const loadingText = typeof I18n !== 'undefined' ? I18n.t('common.loading') : 'Loading…';
    main.innerHTML = `
      <div class="container" style="padding: var(--space-24) 0; text-align: center;">
        <div class="spinner" aria-label="Loading vendor details"></div>
        <p style="margin-top: var(--space-4); color: var(--color-text-muted);">${escapeHtml(loadingText)}</p>
      </div>
    `;
  }
}


function showError(msg) {
  const main = document.getElementById('vendor-detail-main');
  if (main) {
    const errorTitle = typeof I18n !== 'undefined' ? I18n.t('common.error') : 'Oops!';
    const browseBtnText = typeof I18n !== 'undefined' ? I18n.t('vendorDetail.backToVendors') : 'Browse Vendors';
    main.innerHTML = `
      <div class="container">
        <div class="empty-state" style="padding: var(--space-24) 0;">
          <div class="empty-state__icon">😕</div>
          <h3>${escapeHtml(errorTitle)}</h3>
          <p>${escapeHtml(msg)}</p>
          <a href="vendors.html" class="btn btn--primary">${escapeHtml(browseBtnText)}</a>
        </div>
      </div>
    `;
  }
}
