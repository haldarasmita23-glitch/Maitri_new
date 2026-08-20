/**
 * Maitri — Vendors Browse Page JavaScript
 */

let currentCategory = null;
let currentQuery    = '';
let currentSort     = 'rating';

document.addEventListener('DOMContentLoaded', () => {
  // Read URL params
  const params = new URLSearchParams(window.location.search);
  currentCategory = params.get('category') || null;
  currentQuery    = params.get('q') || '';

  // Init filter chips
  buildCategoryFilters();

  // Seed search input
  const searchInput = document.getElementById('vendor-search');
  if (searchInput && currentQuery) {
    searchInput.value = currentQuery;
  }

  // Search live
  searchInput?.addEventListener('input', debounce(e => {
    currentQuery = e.target.value;
    renderVendors();
  }, 300));

  // Sort
  document.getElementById('sort-select')?.addEventListener('change', e => {
    currentSort = e.target.value;
    renderVendors();
  });

  renderVendors();
});


async function buildCategoryFilters() {
  const bar = document.getElementById('category-filter-bar');
  if (!bar) return;

  const categories = await Categories.load();

  const allChip = `
    <button class="filter-chip ${!currentCategory ? 'active' : ''}"
            data-cat="all"
            onclick="selectCategory(null, this)">
      All Vendors
    </button>
  `;

  const chips = categories.map(cat => `
    <button class="filter-chip ${currentCategory === cat.id ? 'active' : ''}"
            data-cat="${cat.id}"
            onclick="selectCategory('${cat.id}', this)">
      ${cat.icon} ${escapeHtml(cat.name)}
    </button>
  `).join('');

  bar.innerHTML = allChip + chips;
}


function selectCategory(categoryId, btn) {
  currentCategory = categoryId;

  // Update chips
  document.querySelectorAll('#category-filter-bar .filter-chip').forEach(c => {
    c.classList.toggle('active', c.dataset.cat === (categoryId || 'all'));
  });

  renderVendors();
}


async function renderVendors() {
  const grid = document.getElementById('vendor-grid');
  const countEl = document.getElementById('results-count');
  if (!grid) return;

  // Load favourite state before rendering so heart buttons reflect the server
  await Favourites.load();

  const all = await Vendors.load();

  // Search + filter (local — the API list is already category/search aware)
  let vendors = filterVendors(all, currentQuery, currentCategory);

  // Sort
  if (currentSort === 'rating') {
    vendors.sort((a, b) => b.averageRating - a.averageRating);
  } else if (currentSort === 'reviews') {
    vendors.sort((a, b) => b.reviewCount - a.reviewCount);
  } else if (currentSort === 'name') {
    vendors.sort((a, b) => a.shopName.localeCompare(b.shopName));
  }

  // Update count
  if (countEl) {
    countEl.textContent = `${vendors.length} vendor${vendors.length !== 1 ? 's' : ''} found`;
  }

  // Render
  if (vendors.length === 0) {
    grid.innerHTML = `
      <div class="no-results">
        <span class="no-results__emoji">🔍</span>
        <h3>No vendors found</h3>
        <p>Try a different search term or browse all categories.</p>
        <button class="btn btn--outline" onclick="clearSearch()">Clear Search</button>
      </div>
    `;
    return;
  }

  grid.innerHTML = vendors.map(v => buildVendorCard(v)).join('');
}


function clearSearch() {
  currentQuery = '';
  currentCategory = null;
  const input = document.getElementById('vendor-search');
  if (input) input.value = '';
  buildCategoryFilters();
  renderVendors();
}


/** Case-insensitive category + keyword filtering over the loaded vendor list. */
function filterVendors(vendors, query, categoryId) {
  let list = vendors;
  if (categoryId) {
    list = list.filter(v => v.categoryId === categoryId);
  }

  const q = query.trim().toLowerCase();
  if (q) {
    list = list.filter(v =>
      (v.shopName || '').toLowerCase().includes(q) ||
      (v.description || '').toLowerCase().includes(q) ||
      (v.area || '').toLowerCase().includes(q) ||
      (v.tags || []).some(t => t.toLowerCase().includes(q))
    );
  }

  return list.slice();
}


// Shared vendor card builder (same as home.js — both loaded on vendors page)
function buildVendorCard(v) {
  const open = isVendorOpen(v.openingTime, v.closingTime);
  const isFav = Favourites.has(v.id);
  return `
    <a href="vendor-detail.html?id=${v.id}" class="vendor-card" aria-label="${v.shopName}">
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
  const added = await Favourites.toggle(vendorId);
  if (added === null) return; // login / blocked prompt already shown
  btn.innerHTML = added ? '❤️' : '🤍';
  btn.classList.toggle('active', added);
  const vendors = await Vendors.load();
  const v = vendors.find(x => x.id === vendorId);
  Toast[added ? 'success' : 'info'](
    added ? 'Added to favourites' : 'Removed from favourites',
    v ? v.shopName : ''
  );
}


function debounce(fn, delay) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}
