/**
 * Maitri — Home / Landing Page JavaScript
 */

document.addEventListener('DOMContentLoaded', () => {

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
});


async function renderCategoryCards() {
  const grid = document.getElementById('category-grid');
  if (!grid) return;

  const categories = await Categories.load();
  const counts = await Vendors.countByCategory();

  grid.innerHTML = categories.map(cat => `
    <a href="vendors.html?category=${encodeURIComponent(cat.id)}"
       class="category-card animate-on-scroll"
       aria-label="Browse ${escapeHtml(cat.name)} vendors">
      <div class="category-card__icon" aria-hidden="true">${cat.icon}</div>
      <span class="category-card__name">${escapeHtml(cat.name)}</span>
      <span class="category-card__count">${counts[cat.id] ?? cat.vendorCount} vendors</span>
    </a>
  `).join('');
}


async function renderFeaturedVendors() {
  const grid = document.getElementById('featured-vendors-grid');
  if (!grid) return;

  // Pick top-rated vendors (3 highest rated) from the live vendor list
  const featured = (await Vendors.load())
    .sort((a, b) => b.averageRating - a.averageRating)
    .slice(0, 3);

  grid.innerHTML = featured.map(v => buildVendorCard(v)).join('');
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
}


function buildVendorCard(v) {
  const open = isVendorOpen(v.openingTime, v.closingTime);
  const isFav = Favourites.has(v.id);
  return `
    <a href="pages/vendor-detail.html?id=${v.id}" class="vendor-card" aria-label="${v.shopName}">
      <div class="vendor-card__image-wrapper">
        <div class="vendor-card__image-placeholder" aria-hidden="true">${v.emoji}</div>
        <span class="vendor-card__badge">
          <span class="badge badge--primary">${v.categoryName}</span>
        </span>
        <button class="vendor-card__fav ${isFav ? 'active' : ''}"
                aria-label="${isFav ? 'Remove from favourites' : 'Add to favourites'}"
                data-vendor-id="${v.id}"
                onclick="event.preventDefault(); toggleFav(this, '${v.id}')">
          ${isFav ? '❤️' : '🤍'}
        </button>
      </div>
      <div class="vendor-card__body">
        <div class="vendor-card__name">${v.shopName}</div>
        <div class="vendor-card__address">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0118 0z"/><circle cx="12" cy="10" r="3"/></svg>
          ${v.area}
        </div>
        <div class="vendor-card__footer">
          <span class="rating">
            ${renderStars(v.averageRating, 'sm')}
            <span class="rating__value">${v.averageRating}</span>
            <span class="rating__count">(${v.reviewCount})</span>
          </span>
          <span class="vendor-card__hours">
            <span class="status-dot ${open ? 'status-dot--green' : 'status-dot--red'}"></span>
            ${open ? 'Open' : 'Closed'}
          </span>
        </div>
      </div>
    </a>
  `;
}


async function toggleFav(btn, vendorId) {
  const added = Favourites.toggle(vendorId);
  btn.innerHTML = added ? '❤️' : '🤍';
  btn.classList.toggle('active', added);
  btn.setAttribute('aria-label', added ? 'Remove from favourites' : 'Add to favourites');
  const vendors = await Vendors.load();
  Toast[added ? 'success' : 'info'](
    added ? 'Added to favourites' : 'Removed from favourites',
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
