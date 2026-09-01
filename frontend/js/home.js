/**
 * Maitri — Home / Landing Page JavaScript
 */

document.addEventListener('DOMContentLoaded', () => {

  // ── Initialise Navbar (auth links, mobile menu, active links)
  Navbar.init();

  // ── Render Category Cards ────────────────────────────────────
  renderCategoryCards();

  // ── Render Featured Vendors ──────────────────────────────────
  renderFeaturedVendors();

  // ── Render Testimonials ──────────────────────────────────────
  renderTestimonials();

  // ── Hero Search ──────────────────────────────────────────────
  initHeroSearch();

  // ── Animated stats counter ───────────────────────────────────
  initCounters();

  // ── Listen to language changes ───────────────────────────────
  window.addEventListener('maitri:language-change', () => {
    renderCategoryCards();
    renderFeaturedVendors();
  });
});


async function renderCategoryCards() {
  const grid = document.getElementById('category-grid');
  if (!grid) return;

  const categories = await Categories.load();
  const counts = await Vendors.countByCategory();

  grid.innerHTML = categories.map(cat => {
    const localizedName = typeof I18n !== 'undefined' ? I18n.translateCategory(cat.name) : cat.name;
    const countVal = counts[cat.id] ?? cat.vendorCount;
    const countText = typeof I18n !== 'undefined' ? I18n.t('home.vendorsCount', { count: countVal }) : `${countVal} vendors`;
    return `
    <a href="pages/vendors.html?category=${encodeURIComponent(cat.id)}"
       class="category-card animate-on-scroll"
       aria-label="${typeof I18n !== 'undefined' ? I18n.t('categories.browseCategory', { category: localizedName }) : `Browse ${escapeHtml(cat.name)} vendors`}">
      <div class="category-card__icon" aria-hidden="true">${cat.icon}</div>
      <span class="category-card__name">${escapeHtml(localizedName)}</span>
      <span class="category-card__count">${countText}</span>
    </a>
  `;
  }).join('');

  if (typeof observeScrollAnimations === 'function') {
    observeScrollAnimations(grid);
  }
}


async function renderFeaturedVendors() {
  const grid = document.getElementById('featured-vendors-grid');
  if (!grid) return;

  // Load favourite state before rendering so heart buttons reflect the server
  await Favourites.load();

  // Pick top-rated vendors (3 highest rated) from the live vendor list
  const featured = (await Vendors.load())
    .sort((a, b) => b.averageRating - a.averageRating)
    .slice(0, 3);

  grid.innerHTML = featured.map(v => buildVendorCard(v)).join('');

  if (typeof observeScrollAnimations === 'function') {
    observeScrollAnimations(grid);
  }
}


function renderTestimonials() {
  const grid = document.getElementById('testimonials-grid');
  if (!grid) return;

  grid.innerHTML = MOCK_TESTIMONIALS.map(t => `
    <div class="testimonial-card animate-on-scroll">
      <p class="testimonial-card__quote">${t.quote}</p>
      <div class="testimonial-card__author">
        <div class="testimonial-card__avatar" aria-hidden="true">${t.initial}</div>
        <div>
          <div class="testimonial-card__name">${t.name}</div>
          <div class="testimonial-card__role">${t.role}</div>
        </div>
      </div>
    </div>
  `).join('');

  if (typeof observeScrollAnimations === 'function') {
    observeScrollAnimations(grid);
  }
}


function buildVendorCard(v) {
  const vendorId = v.id || v._id;
  const open = isVendorOpen(v.openingTime, v.closingTime);
  const isFav = Favourites.has(vendorId);
  const localizedCat = typeof I18n !== 'undefined' ? I18n.translateCategory(v.categoryName) : v.categoryName;
  const localizedArea = typeof I18n !== 'undefined' ? I18n.translateArea(v.area) : v.area;
  const statusLabel = open
    ? (typeof I18n !== 'undefined' ? I18n.t('common.openNow') : 'Open')
    : (typeof I18n !== 'undefined' ? I18n.t('common.closed') : 'Closed');
  const favLabel = isFav
    ? (typeof I18n !== 'undefined' ? I18n.t('vendors.removeFav') : 'Remove from favourites')
    : (typeof I18n !== 'undefined' ? I18n.t('vendors.addFav') : 'Add to favourites');

  return `
    <a href="pages/vendor-detail.html?id=${encodeURIComponent(vendorId)}" class="vendor-card" data-vendor-id="${vendorId}" aria-label="${escapeHtml(v.shopName)}">
      <div class="vendor-card__image-wrapper">
        <div class="vendor-card__image-placeholder" aria-hidden="true">${v.emoji}</div>
        <span class="vendor-card__badge">
          <span class="badge badge--primary">${escapeHtml(localizedCat)}</span>
        </span>
        <button class="vendor-card__fav ${isFav ? 'active' : ''}"
                aria-label="${favLabel}"
                data-vendor-id="${vendorId}"
                onclick="event.preventDefault(); toggleFav(this, '${vendorId}')">
          ${isFav ? '❤️' : '🤍'}
        </button>
      </div>
      <div class="vendor-card__body">
        <div class="vendor-card__name">${escapeHtml(v.shopName)}</div>
        <div class="vendor-card__address">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/><circle cx="12" cy="10" r="3"/></svg>
          ${escapeHtml(localizedArea)}
        </div>
        <div class="vendor-card__footer">
          <span class="rating">
            ${renderStars(v.averageRating, 'sm')}
            <span class="rating__value">${v.averageRating}</span>
            <span class="rating__count">(${v.reviewCount})</span>
          </span>
          <span class="vendor-card__hours">
            <span class="status-dot ${open ? 'status-dot--green' : 'status-dot--red'}"></span>
            ${statusLabel}
          </span>
        </div>
      </div>
    </a>
  `;
}


async function toggleFav(btn, vendorId) {
  const added = await Favourites.toggle(vendorId);
  if (added === null) return; // login / blocked prompt already shown
  btn.innerHTML = added ? '❤️' : '🤍';
  btn.classList.toggle('active', added);
  const favLabel = added
    ? (typeof I18n !== 'undefined' ? I18n.t('vendors.removeFav') : 'Remove from favourites')
    : (typeof I18n !== 'undefined' ? I18n.t('vendors.addFav') : 'Add to favourites');
  btn.setAttribute('aria-label', favLabel);
  const vendors = await Vendors.load();
  const title = added
    ? (typeof I18n !== 'undefined' ? I18n.t('messages.favAdded') : 'Added to favourites')
    : (typeof I18n !== 'undefined' ? I18n.t('messages.favRemoved') : 'Removed from favourites');
  Toast[added ? 'success' : 'info'](
    title,
    added ? `${vendors.find(v => v.id === vendorId)?.shopName} saved!` : ''
  );
}


function initHeroSearch() {
  const form = document.getElementById('hero-search-form');
  const input = document.getElementById('hero-search-input');
  if (!form || !input) return;

  form.addEventListener('submit', e => {
    e.preventDefault();
    const q = input.value.trim();
    if (q) {
      window.location.href = `pages/vendors.html?q=${encodeURIComponent(q)}`;
    } else {
      window.location.href = 'pages/vendors.html';
    }
  });
}


function initCounters() {
  const counters = document.querySelectorAll('[data-count]');
  if (!counters.length) return;

  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      const el = entry.target;
      const target = parseInt(el.dataset.count, 10);
      let current = 0;
      const step = Math.ceil(target / 40);
      const interval = setInterval(() => {
        current = Math.min(current + step, target);
        el.textContent = current + (el.dataset.suffix || '');
        if (current >= target) clearInterval(interval);
      }, 40);
      observer.unobserve(el);
    });
  }, { threshold: 0.5 });

  counters.forEach(el => observer.observe(el));
}
