/**
 * Maitri — Categories (Phase 4)
 *
 * Loads categories from the backend API (GET /api/categories) with a
 * MOCK_CATEGORIES fallback so the site still works when the backend is
 * unreachable.
 *
 * Each category is normalized to the shape the rest of the frontend expects:
 *   { id, slug, name, icon, vendorCount }
 *   id = slug — the stable URL key used in vendors.html?category=...
 */

/** Emoji shown for each seeded category slug. New categories get a default tag. */
const CATEGORY_ICONS = {
  'street-food': '🍛',
  'tailors': '🧵',
  'printing': '🖨️',
  'repair': '📱',
};

/** Mock categories indexed by slug, so we can carry over placeholder counts. */
const MOCK_CATEGORIES_BY_SLUG = Object.fromEntries(
  MOCK_CATEGORIES.map(c => [c.id, c])
);

const Categories = {
  cache: null,
  fromApi: false,

  /**
   * Returns the category list, loading it from the API on first call.
   * Falls back to MOCK_CATEGORIES when the backend is offline.
   */
  async load() {
    if (this.cache) return this.cache;

    try {
      const response = await API.get('/categories');
      const items = response && response.success ? response.data : null;
      if (Array.isArray(items) && items.length) {
        this.fromApi = true;
        this.cache = items.map(toCategoryCard);
        return this.cache;
      }
    } catch {
      // Backend unreachable — fall through to mock data
    }

    this.fromApi = false;
    this.cache = MOCK_CATEGORIES.map(c => ({ ...c, slug: c.id }));
    return this.cache;
  },
};

/** Maps an API category document to the frontend category card shape. */
function toCategoryCard(category) {
  const mock = MOCK_CATEGORIES_BY_SLUG[category.slug];
  return {
    id: category.slug,
    slug: category.slug,
    name: category.categoryName,
    icon: CATEGORY_ICONS[category.slug] || '🏷️',
    // Vendor counts arrive with the vendor module (Phase 5). Until then, keep
    // the mock count for the 4 seeded categories so the UI reads naturally.
    vendorCount: mock ? mock.vendorCount : 0,
  };
}

/** Escapes text before inserting into innerHTML (category names are admin-provided). */
function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
