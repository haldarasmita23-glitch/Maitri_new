/**
 * Maitri — Vendor Detail Page JavaScript (Phase 7: Reviews Integration)
 */

document.addEventListener('DOMContentLoaded', async () => {
  const params = new URLSearchParams(window.location.search);
  const vendorId = params.get('id');

  if (!vendorId) {
    showError('No vendor ID specified.');
    return;
  }

  const vendor = await Vendors.getById(vendorId);
  if (!vendor) {
    showError('Vendor not found.');
    return;
  }

  renderVendorDetail(vendor);
  initTabs();
  initRatingInput();
  await Favourites.load();
  initFavButton(vendor.id);

  const currentUser = getCurrentUser();
  const myReview = await renderReviews(vendor.id, currentUser);
  await initReviewForm(vendor.id, currentUser, myReview);
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
    catEl.innerHTML = `<span class="badge badge--primary">${v.emoji} ${v.categoryName}</span>`;
  }

  // Rating
  const ratingEl = document.getElementById('vendor-rating');
  if (ratingEl) {
    ratingEl.innerHTML = `
      <span class="rating">
        ${renderStars(v.averageRating, 'lg')}
        <span class="rating__value font-bold">${v.averageRating}</span>
        <span class="rating__count">(${v.reviewCount} reviews)</span>
      </span>
    `;
  }

  // Open status
  const statusEl = document.getElementById('vendor-status');
  if (statusEl) {
    const open = isVendorOpen(v.openingTime, v.closingTime);
    statusEl.innerHTML = `
      <span class="status-dot ${open ? 'status-dot--green status-dot--pulse' : 'status-dot--red'}"></span>
      <span class="sidebar-status__value ${open ? 'open' : 'closed'}">${open ? 'Open Now' : 'Closed'}</span>
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
      `<span class="badge badge--gray">${t}</span>`
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
    return (typeof Navbar !== 'undefined' && Navbar.storedUser())
      || JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA))
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
  if (!window.confirm('Delete your review? This cannot be undone.')) return;

  try {
    const result = await API.deleteReview(review.id);
    if (result.success) {
      Toast.success('Review deleted.', 'Your review has been removed.');
      setReviewFormMode('create');
      const currentUser = getCurrentUser();
      await renderReviews(vendorId, currentUser);
    } else {
      Toast.error('Could not delete review', result.message || 'Please try again.');
    }
  } catch {
    Toast.error('Network error', 'Please check your connection and try again.');
  }
}

async function initReviewForm(vendorId, currentUser, myReview) {
  const form = document.getElementById('review-form');
  if (!form) return;

  const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);

  // Logged out — ask the visitor to log in
  if (!token || !currentUser) {
    const writeReviewEl = document.querySelector('.write-review');
    if (writeReviewEl) {
      writeReviewEl.innerHTML = `
        <h4>Write a Review</h4>
        <div class="alert alert--info" role="note">
          <span class="alert__icon">🔐</span>
          <div class="alert__text">
            <strong>Log in required</strong><br>
            Please <a href="login.html">log in</a> to write a review for this vendor.
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
      writeReviewEl.innerHTML = `
        <h4>Write a Review</h4>
        <div class="alert alert--warning" role="note">
          <span class="alert__icon">⚠️</span>
          <div class="alert__text">
            <strong>Business accounts cannot write reviews</strong><br>
            Vendor accounts are not allowed to submit reviews to maintain authenticity.
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
    submitBtn.textContent = 'Saving...';

    try {
      const rating = parseInt(document.getElementById('rating-value')?.value || '0', 10);
      const reviewText = document.getElementById('review-text')?.value.trim();

      if (!rating) {
        Toast.warning('Rating required', 'Please select a star rating.');
        return;
      }
      if (reviewText && reviewText.length < 10) {
        Toast.warning('Review too short', 'Please write at least 10 characters, or leave it empty.');
        return;
      }

      let result;
      if (reviewFormState.mode === 'edit' && reviewFormState.reviewId) {
        result = await API.updateReview(reviewFormState.reviewId, { rating, reviewText: reviewText || null });
      } else {
        result = await API.submitReview({ vendorId, rating, reviewText: reviewText || null });
      }

      if (result.success) {
        Toast.success(reviewFormState.mode === 'edit' ? 'Review updated!' : 'Review submitted!',
          'Thank you for your feedback.');

        // Refresh the list + summary, then keep the form in edit mode with the saved values
        const updated = result.data?.data || null;
        const refreshed = await renderReviews(vendorId, getCurrentUser());
        setReviewFormMode('edit', updated || refreshed || null);
      } else {
        if (result.message && result.message.includes('already reviewed')) {
          Toast.warning('Already reviewed', 'You have already reviewed this vendor. You can edit your existing review instead.');
          const refreshed = await renderReviews(vendorId, getCurrentUser());
          if (refreshed) setReviewFormMode('edit', refreshed);
        } else {
          Toast.error('Submission failed', result.message || 'Please try again.');
        }
      }
    } catch (error) {
      console.error('Failed to submit review:', error);
      Toast.error('Network error', 'Please check your connection and try again.');
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


function showError(msg) {
  const main = document.getElementById('vendor-detail-main');
  if (main) {
    main.innerHTML = `
      <div class="container">
        <div class="empty-state" style="padding: var(--space-24) 0;">
          <div class="empty-state__icon">😕</div>
          <h3>Oops!</h3>
          <p>${msg}</p>
          <a href="vendors.html" class="btn btn--primary">Browse Vendors</a>
        </div>
      </div>
    `;
  }
}
