/**
 * Maitri — Vendor Detail Page JavaScript
 */

document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search);
  const vendorId = params.get('id');

  if (!vendorId) {
    showError('No vendor ID specified.');
    return;
  }

  const vendor = getVendorById(vendorId);
  if (!vendor) {
    showError('Vendor not found.');
    return;
  }

  renderVendorDetail(vendor);
  renderReviews(vendor);
  initTabs();
  initRatingInput();
  initReviewForm(vendor);
  initFavButton(vendor.id);
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
    const cat = getCategoryById(v.categoryId);
    catEl.innerHTML = `<span class="badge badge--primary">${cat?.icon || ''} ${v.categoryName}</span>`;
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
}


function renderReviews(v) {
  const reviews = getReviewsForVendor(v.id);

  // Summary
  const bigScore = document.getElementById('reviews-big-score');
  if (bigScore) bigScore.textContent = v.averageRating.toFixed(1);

  const summaryStars = document.getElementById('reviews-summary-stars');
  if (summaryStars) summaryStars.innerHTML = renderStars(v.averageRating, 'lg');

  const summaryCount = document.getElementById('reviews-summary-count');
  if (summaryCount) summaryCount.textContent = `${v.reviewCount} reviews`;

  // Rating bars
  const barsEl = document.getElementById('rating-bars');
  if (barsEl) {
    const dist = getRatingDistribution(reviews);
    const total = reviews.length || 1;
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
  if (!listEl) return;

  if (reviews.length === 0) {
    listEl.innerHTML = `
      <div class="empty-state">
        <div class="empty-state__icon">⭐</div>
        <h3>No reviews yet</h3>
        <p>Be the first to review this vendor!</p>
      </div>
    `;
    return;
  }

  listEl.innerHTML = reviews.map(r => `
    <div class="review-card">
      <div class="review-card__header">
        <div class="review-card__user">
          <div class="review-card__avatar" aria-hidden="true">${r.initial}</div>
          <div>
            <div class="review-card__name">${r.userName}</div>
            <div class="review-card__date">${formatDate(r.date)}</div>
          </div>
        </div>
        <span class="rating">
          ${renderStars(r.rating, 'sm')}
        </span>
      </div>
      <p class="review-card__text">${r.text}</p>
    </div>
  `).join('');
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


function initReviewForm(v) {
  const form = document.getElementById('review-form');
  if (!form) return;

  form.addEventListener('submit', e => {
    e.preventDefault();
    const rating = parseInt(document.getElementById('rating-value')?.value || '0', 10);
    const text   = document.getElementById('review-text')?.value.trim();

    if (!rating) {
      Toast.warning('Rating required', 'Please select a star rating.');
      return;
    }
    if (!text || text.length < 10) {
      Toast.warning('Review too short', 'Please write at least 10 characters.');
      return;
    }

    // Phase 2: show success message, no API call
    Toast.success('Review submitted!', 'Thank you. Your review will appear after approval.');
    form.reset();
    document.querySelectorAll('.rating-input__star').forEach(s => s.classList.remove('active'));
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

  btn.addEventListener('click', () => {
    const added = Favourites.toggle(vendorId);
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
