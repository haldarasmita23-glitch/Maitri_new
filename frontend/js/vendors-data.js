/**
 * Maitri — Vendors data (Phase 5)
 *
 * Loads approved vendors from the backend API (GET /api/vendors) with a
 * MOCK_VENDORS fallback so the site still works when the backend is offline.
 *
 * Each vendor is normalized to the shape the existing UI already expects
 * (id, shopName, categoryId = SLUG, categoryName, emoji, hours, rating, ...)
 * so buildVendorCard / vendor-detail rendering keep working unchanged.
 *
 * Requires: api.js, mock-data.js, categories.js (for CATEGORY_ICONS).
 */

const Vendors = {
  cache: null,
  fromApi: false,

  /**
   * Returns the list of approved vendors, loading from the API on first call.
   * Falls back to MOCK_VENDORS when the backend is unreachable.
   */
  async load() {
    if (this.cache) return this.cache;

    try {
      const response = await API.getVendors();
      const items = response && response.success ? response.data : null;
      if (Array.isArray(items) && items.length > 0) {
        this.fromApi = true;
        this.cache = items.map(normalizeVendor);
        return this.cache;
      }
      // API returned empty array — fall through to mock data
    } catch {
      // Backend unreachable — fall through to mock data
    }

    this.fromApi = false;
    this.cache = MOCK_VENDORS.map(v => ({ ...v }));
    return this.cache;
  },

  /**
   * Fetches one vendor by id. Prefers the real detail endpoint; falls back
   * to the loaded cache (mock data) when the backend is unreachable.
   */
  async getById(id) {
    console.log('[Vendors.getById] Called with id:', id);
    try {
      const response = await API.getVendor(id);
      console.log('[Vendors.getById] API.getVendor response:', JSON.stringify(response));
      if (response && response.success && response.data) {
        const normalized = normalizeVendor(response.data);
        console.log('[Vendors.getById] Normalized vendor id:', normalized.id);
        return normalized;
      }
      console.warn('[Vendors.getById] API response not successful or no data:', response);
    } catch (err) {
      console.error('[Vendors.getById] API call threw:', err);
    }
    const all = await this.load();
    console.log('[Vendors.getById] Searching cache for id:', id, '| cache size:', all.length);
    const found = all.find(v => v.id === id);
    if (found) return found;

    // Graceful fallback for mock IDs (e.g. v001-v012) when backend is active
    if (typeof MOCK_VENDORS !== 'undefined' && Array.isArray(MOCK_VENDORS)) {
      const mockFound = MOCK_VENDORS.find(v => v.id === id);
      if (mockFound) return { ...mockFound };
    }

    console.error('[Vendors.getById] Vendor not found for id:', id);
    return null;
  },

  /**
   * Returns a map of category slug → approved vendor count.
   * Used by the home page category grid when real data is available.
   */
  async countByCategory() {
    const all = await this.load();
    const counts = {};
    all.forEach(v => {
      const key = v.categoryId; // the category slug
      counts[key] = (counts[key] || 0) + 1;
    });
    return counts;
  },
};

/** Maps an API VendorResponse to the frontend vendor card/detail shape. */
function normalizeVendor(v) {
  return {
    id: v.id || v._id,
    userId: v.userId || (v.id ? 'user-' + v.id : ''),               // owner's user account id (for chat)
    shopName: v.shopName,
    ownerName: v.ownerName,
    categoryId: v.categorySlug,     // frontend identity is the slug
    categoryName: v.categoryName,
    description: v.description || '',
    address: v.address || '',
    area: v.area || '',
    phone: v.phone || '',
    openingTime: v.openingTime,
    closingTime: v.closingTime,
    averageRating: Number(v.averageRating || 0),
    reviewCount: 0,                 // reviews ship in a later phase
    images: v.images || [],
    emoji: CATEGORY_ICONS[v.categorySlug] || '🏪',
    verified: v.status === 'APPROVED',
    status: v.status,
    tags: [],                       // tags ship in a later phase
  };
}
