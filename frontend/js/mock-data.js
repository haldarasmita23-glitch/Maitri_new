/**
 * Maitri — Mock Data
 *
 * Realistic placeholder data for Phase 2.
 * Will be replaced by real API calls in Phase 4 and 5.
 */

const MOCK_CATEGORIES = [
  {
    id: 'street-food',
    name: 'Street Food',
    icon: '🍛',
    description: 'Local eateries, tiffin centres, and street food stalls',
    vendorCount: 4,
  },
  {
    id: 'tailors',
    name: 'Tailors',
    icon: '🧵',
    description: 'Clothing alterations, custom stitching, and embroidery',
    vendorCount: 3,
  },
  {
    id: 'printing',
    name: 'Printing & Xerox',
    icon: '🖨️',
    description: 'Document printing, lamination, and photocopying',
    vendorCount: 2,
  },
  {
    id: 'repair',
    name: 'Mobile/Laptop Repair',
    icon: '📱',
    description: 'Mobile phone and laptop servicing and repair',
    vendorCount: 3,
  },
];

const MOCK_VENDORS = [
  // ── Street Food ──────────────────────────────────────────────
  {
    id: 'v001',
    shopName: 'Shree Sagar Tiffin Centre',
    ownerName: 'Ramesh Kumar',
    categoryId: 'street-food',
    categoryName: 'Street Food',
    description: 'Authentic South Indian breakfast and lunch since 1998. Known for our crispy dosas and filter coffee. A Peenya favourite for over two decades.',
    address: 'Near Gate 2, Peenya Industrial Area, Bengaluru',
    area: 'Peenya',
    phone: '+91 98450 12345',
    openingTime: '06:30',
    closingTime: '14:00',
    averageRating: 4.6,
    reviewCount: 87,
    images: [],
    emoji: '🍛',
    verified: true,
    status: 'APPROVED',
    tags: ['Dosa', 'Idli', 'Filter Coffee', 'Veg'],
  },
  {
    id: 'v002',
    shopName: 'Annapoorna Mess',
    ownerName: 'Suresh Bhat',
    categoryId: 'street-food',
    categoryName: 'Street Food',
    description: 'Home-style North Karnataka thali meals. Full meals available for lunch. Popular among factory workers and students.',
    address: 'Nagasandra Main Road, near Nagasandra Metro Station',
    area: 'Nagasandra',
    phone: '+91 97400 55678',
    openingTime: '11:00',
    closingTime: '15:30',
    averageRating: 4.3,
    reviewCount: 62,
    images: [],
    emoji: '🥘',
    verified: true,
    status: 'APPROVED',
    tags: ['Thali', 'Lunch', 'Veg & Non-Veg'],
  },
  {
    id: 'v003',
    shopName: 'Peenya Juice Corner',
    ownerName: 'Mohammed Salim',
    categoryId: 'street-food',
    categoryName: 'Street Food',
    description: 'Fresh fruit juices, sugarcane juice, and shakes. Best sugarcane juice in the area — no added sugar.',
    address: 'Peenya 2nd Stage, near Bus Stand',
    area: 'Peenya',
    phone: '+91 96060 34567',
    openingTime: '08:00',
    closingTime: '20:00',
    averageRating: 4.8,
    reviewCount: 115,
    images: [],
    emoji: '🥤',
    verified: true,
    status: 'APPROVED',
    tags: ['Juices', 'Shakes', 'Fresh', 'Cold Drinks'],
  },
  {
    id: 'v004',
    shopName: 'Meghana Fast Food',
    ownerName: 'Lakshmi Devi',
    categoryId: 'street-food',
    categoryName: 'Street Food',
    description: 'Puri bhaji, gobi manchurian, and evening snacks. Very popular during evening hours.',
    address: '3rd Cross, Nagasandra, Bengaluru',
    area: 'Nagasandra',
    phone: '+91 99720 11234',
    openingTime: '16:00',
    closingTime: '21:30',
    averageRating: 4.1,
    reviewCount: 43,
    images: [],
    emoji: '🍱',
    verified: true,
    status: 'APPROVED',
    tags: ['Snacks', 'Evening', 'Veg'],
  },

  // ── Tailors ──────────────────────────────────────────────────
  {
    id: 'v005',
    shopName: 'New Style Tailors',
    ownerName: 'Gopal Naidu',
    categoryId: 'tailors',
    categoryName: 'Tailors',
    description: `Expert tailoring for men's and women's clothing. Specialise in salwar kameez, kurta, and formal shirts. 20+ years of experience.`,
    address: 'Peenya 1st Stage, Main Road, Bengaluru',
    area: 'Peenya',
    phone: '+91 99001 78900',
    openingTime: '10:00',
    closingTime: '19:30',
    averageRating: 4.5,
    reviewCount: 54,
    images: [],
    emoji: '🧵',
    verified: true,
    status: 'APPROVED',
    tags: ['Stitching', 'Alterations', 'Ladies & Gents'],
  },
  {
    id: 'v006',
    shopName: 'Divya Fashion Boutique',
    ownerName: 'Divya Menon',
    categoryId: 'tailors',
    categoryName: 'Tailors',
    description: 'Ladies boutique specialising in saree blouses, lehenga, and designer kurtas. Embroidery work available.',
    address: 'Nagasandra, near Metro Pillar 142',
    area: 'Nagasandra',
    phone: '+91 88004 67890',
    openingTime: '10:30',
    closingTime: '20:00',
    averageRating: 4.7,
    reviewCount: 38,
    images: [],
    emoji: '👗',
    verified: true,
    status: 'APPROVED',
    tags: ['Blouses', 'Lehenga', 'Embroidery', 'Ladies'],
  },
  {
    id: 'v007',
    shopName: 'Raja Gents Tailor',
    ownerName: 'Rajendra Sharma',
    categoryId: 'tailors',
    categoryName: 'Tailors',
    description: 'Formal shirts, trousers, and suits for men. Quick turnaround time. Uniforms for industries also accepted.',
    address: 'Peenya Industrial Area, Block C',
    area: 'Peenya',
    phone: '+91 98300 22100',
    openingTime: '09:00',
    closingTime: '18:00',
    averageRating: 4.2,
    reviewCount: 29,
    images: [],
    emoji: '👔',
    verified: true,
    status: 'APPROVED',
    tags: ['Formal', 'Shirts', 'Uniforms', 'Gents'],
  },

  // ── Printing & Xerox ─────────────────────────────────────────
  {
    id: 'v008',
    shopName: 'Peenya Xerox & Prints',
    ownerName: 'Vinod Kumar',
    categoryId: 'printing',
    categoryName: 'Printing & Xerox',
    description: 'Photocopying, colour printing, lamination, and spiral binding. Open early for morning document needs.',
    address: 'Near Peenya Metro Station, Ground Floor',
    area: 'Peenya',
    phone: '+91 98860 90001',
    openingTime: '07:30',
    closingTime: '21:00',
    averageRating: 4.4,
    reviewCount: 72,
    images: [],
    emoji: '🖨️',
    verified: true,
    status: 'APPROVED',
    tags: ['Xerox', 'Printing', 'Lamination', 'Binding'],
  },
  {
    id: 'v009',
    shopName: 'Digital Print House',
    ownerName: 'Praveen S',
    categoryId: 'printing',
    categoryName: 'Printing & Xerox',
    description: 'High-quality digital colour printing, visiting cards, banners, and ID card printing. Bulk orders welcome.',
    address: 'Nagasandra Main Road, 2nd Floor',
    area: 'Nagasandra',
    phone: '+91 95380 45600',
    openingTime: '09:00',
    closingTime: '19:30',
    averageRating: 4.5,
    reviewCount: 41,
    images: [],
    emoji: '🖼️',
    verified: true,
    status: 'APPROVED',
    tags: ['Colour Print', 'Visiting Cards', 'Banners', 'Digital'],
  },

  // ── Mobile/Laptop Repair ─────────────────────────────────────
  {
    id: 'v010',
    shopName: 'TechFix Solutions',
    ownerName: 'Arjun Reddy',
    categoryId: 'repair',
    categoryName: 'Mobile/Laptop Repair',
    description: 'Certified technician for all mobile phones and laptops. Screen replacement, battery, charging port, water damage repair. 3-month warranty on parts.',
    address: 'Peenya 2nd Stage, Shop 7, Ground Floor',
    area: 'Peenya',
    phone: '+91 87940 33211',
    openingTime: '10:00',
    closingTime: '20:00',
    averageRating: 4.7,
    reviewCount: 96,
    images: [],
    emoji: '📱',
    verified: true,
    status: 'APPROVED',
    tags: ['Screen Repair', 'Battery', 'All Brands', 'Warranty'],
  },
  {
    id: 'v011',
    shopName: 'Nagasandra Mobile Care',
    ownerName: 'Imran Sheikh',
    categoryId: 'repair',
    categoryName: 'Mobile/Laptop Repair',
    description: 'Quick mobile repair and accessories shop. Software unlocking, data recovery, and protective screen fitting.',
    address: 'Nagasandra Circle, opp. SBI Bank',
    area: 'Nagasandra',
    phone: '+91 96560 78900',
    openingTime: '09:30',
    closingTime: '21:00',
    averageRating: 4.2,
    reviewCount: 58,
    images: [],
    emoji: '🔧',
    verified: true,
    status: 'APPROVED',
    tags: ['Software', 'Data Recovery', 'Accessories'],
  },
  {
    id: 'v012',
    shopName: 'Laptop Doctor',
    ownerName: 'Anand Prasad',
    categoryId: 'repair',
    categoryName: 'Mobile/Laptop Repair',
    description: 'Laptop and desktop specialist. RAM upgrades, SSD installation, OS installation, virus removal. Home service available.',
    address: 'Peenya Industrial Area, 4th Cross',
    area: 'Peenya',
    phone: '+91 99870 12000',
    openingTime: '09:00',
    closingTime: '19:00',
    averageRating: 4.6,
    reviewCount: 67,
    images: [],
    emoji: '💻',
    verified: true,
    status: 'APPROVED',
    tags: ['Laptops', 'Desktops', 'Home Service', 'SSD', 'OS'],
  },
];

const MOCK_REVIEWS = {
  'v001': [
    { id: 'r1', userName: 'Kavitha S.', initial: 'K', rating: 5, text: 'Best dosa in Peenya! The masala dosa is perfectly crispy and the sambar is excellent. Been coming here for 5 years.', date: '2026-07-15' },
    { id: 'r2', userName: 'Ravi M.', initial: 'R', rating: 4, text: 'Good food, reasonable price. Sometimes crowded on weekday mornings but worth the wait.', date: '2026-07-02' },
    { id: 'r3', userName: 'Priya L.', initial: 'P', rating: 5, text: 'Authentic taste! Reminds me of my grandmother\'s cooking. Filter coffee is a must-try.', date: '2026-06-18' },
  ],
  'v010': [
    { id: 'r4', userName: 'Suresh T.', initial: 'S', rating: 5, text: 'Fixed my phone screen in 40 minutes! Great service and the warranty gave me confidence. Highly recommend.', date: '2026-08-01' },
    { id: 'r5', userName: 'Nalini R.', initial: 'N', rating: 4, text: 'Professional service. Arjun was very knowledgeable and explained the issue clearly. Fair pricing.', date: '2026-07-20' },
    { id: 'r6', userName: 'Deepak B.', initial: 'D', rating: 5, text: 'Data recovery done successfully when I thought all my photos were lost. Lifesaver!', date: '2026-07-10' },
  ],
};

const MOCK_TESTIMONIALS = [
  {
    quote: `Maitri helped me find a good tailor near my home in Nagasandra. I didn't know such skilled artisans were right around the corner!`,
    name: 'Ananya S.',
    role: 'Resident, Nagasandra',
    initial: 'A',
  },
  {
    quote: 'My tiffin centre is now getting new customers every week. Maitri has really helped small shops like mine be discovered.',
    name: 'Ramesh Kumar',
    role: 'Vendor, Street Food',
    initial: 'R',
  },
  {
    quote: 'The repair shop reviews on Maitri saved me from a bad experience. I could check ratings before going.',
    name: 'Pradeep N.',
    role: 'Resident, Peenya',
    initial: 'P',
  },
];

/** Get vendors for a given category, or all vendors */
function getVendorsByCategory(categoryId = null) {
  if (!categoryId || categoryId === 'all') return MOCK_VENDORS;
  return MOCK_VENDORS.filter(v => v.categoryId === categoryId);
}

/** Get a single vendor by id */
function getVendorById(id) {
  return MOCK_VENDORS.find(v => v.id === id) || null;
}

/** Get reviews for a vendor */
function getReviewsForVendor(vendorId) {
  return MOCK_REVIEWS[vendorId] || [];
}

/** Get category by id */
function getCategoryById(id) {
  return MOCK_CATEGORIES.find(c => c.id === id) || null;
}

/** Build rating distribution for a vendor */
function getRatingDistribution(reviews) {
  const dist = { 5: 0, 4: 0, 3: 0, 2: 0, 1: 0 };
  reviews.forEach(r => { dist[r.rating] = (dist[r.rating] || 0) + 1; });
  return dist;
}

/** Check if vendor is currently open */
function isVendorOpen(openingTime, closingTime) {
  const now = new Date();
  const [oH, oM] = openingTime.split(':').map(Number);
  const [cH, cM] = closingTime.split(':').map(Number);
  const currentMinutes = now.getHours() * 60 + now.getMinutes();
  const openMinutes  = oH * 60 + oM;
  const closeMinutes = cH * 60 + cM;
  return currentMinutes >= openMinutes && currentMinutes <= closeMinutes;
}

/** Format time to 12-hour */
function formatTime(t) {
  const [h, m] = t.split(':').map(Number);
  const suffix = h >= 12 ? 'PM' : 'AM';
  const hour = h > 12 ? h - 12 : (h === 0 ? 12 : h);
  return `${hour}:${String(m).padStart(2, '0')} ${suffix}`;
}

/** Render ★ stars as HTML string */
function renderStars(rating, size = '') {
  const sizeClass = size ? `stars--${size}` : '';
  let html = `<span class="stars ${sizeClass}" aria-label="${rating} out of 5 stars">`;
  for (let i = 1; i <= 5; i++) {
    if (rating >= i) {
      html += `<span class="stars__star filled">★</span>`;
    } else if (rating >= i - 0.5) {
      html += `<span class="stars__star partial">★</span>`;
    } else {
      html += `<span class="stars__star">☆</span>`;
    }
  }
  html += '</span>';
  return html;
}

/** Format date to readable string */
function formatDate(dateStr) {
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-IN', { year: 'numeric', month: 'long', day: 'numeric' });
}

/** Simple search across vendor fields */
function searchVendors(query, categoryId = null) {
  const q = query.trim().toLowerCase();
  let vendors = getVendorsByCategory(categoryId);
  if (!q) return vendors;
  return vendors.filter(v =>
    v.shopName.toLowerCase().includes(q) ||
    v.description.toLowerCase().includes(q) ||
    v.area.toLowerCase().includes(q) ||
    v.tags.some(t => t.toLowerCase().includes(q))
  );
}

/**
 * Favourites — API-backed module (Phase 8).
 *
 * Replaces the original localStorage-only mock with backend storage for
 * authenticated USER/ADMIN accounts, while keeping the SAME method surface
 * (get/add/remove/toggle/has) so existing call sites keep working.
 *
 * ─── BEHAVIOUR ──────────────────────────────────────────────────────────────
 *   Logged-in USER/ADMIN  → favourites are stored in the backend (source of truth)
 *   Logged-out user       → old localStorage ids are shown for display only;
 *                           attempting to favourite shows a login prompt
 *   VENDOR                → favourite controls are blocked (no USER operations)
 *   401                   → login prompt (token invalid/expired)
 *   404                   → treated as "not favourited"
 *   Offline / API down    → falls back to localStorage ids (best effort)
 *
 * The in-memory Set is the single source of truth for button state; call
 * `await Favourites.load()` before rendering cards so `has()` reflects the
 * server state.
 */
const Favourites = {
  /** In-memory cache of favourited vendor ids (populated by load()). */
  _ids: new Set(),

  /** Auth key the cache was loaded for (avoids redundant refetches). */
  _loadedFor: null,

  /** The locally stored user (or null). Safe when components.js is absent. */
  currentUser() {
    try {
      return (typeof Navbar !== 'undefined' && Navbar.storedUser())
        || JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA))
        || null;
    } catch {
      return null;
    }
  },

  /** A stable key for the current auth state (used to guard reloads). */
  authKey() {
    const u = this.currentUser();
    return u ? `${u.role}:${u.id || u.email}` : 'anon';
  },

  /** Whether backend favourite operations are permitted for this session. */
  canUseBackend() {
    const u = this.currentUser();
    if (!u || u.role === 'VENDOR') return false;
    return !!localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  },

  /**
   * Populates the in-memory set from the backend (or localStorage when
   * logged out). Call once before rendering favourite buttons.
   */
  async load(force = false) {
    const key = this.authKey();
    if (!force && this._loadedFor === key) return;
    this._ids.clear();
    this._loadedFor = key;

    const u = this.currentUser();

    // Vendors never have favourites.
    if (u && u.role === 'VENDOR') {
      this._ids.clear();
      return;
    }

    if (u && localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN)) {
      try {
        const res = await API.getFavourites();
        if (res.ok && res.data && res.data.success && Array.isArray(res.data.data)) {
          res.data.data.forEach(f => {
            if (f && f.vendorId) this._ids.add(f.vendorId);
          });
        } else if (res.status === 401) {
          this._loadLocalFallback();
        }
      } catch {
        // Backend unreachable — fall back to local ids (best effort).
        this._loadLocalFallback();
      }
    } else {
      // Logged out — keep local ids visible for display compatibility.
      this._loadLocalFallback();
    }
  },

  /** All favourited vendor ids (in-memory). */
  get() {
    return Array.from(this._ids);
  },

  /** Whether a vendor is currently favourited (in-memory). */
  has(vendorId) {
    return this._ids.has(vendorId);
  },

  /**
   * Adds a vendor to favourites.
   * @returns {Promise<boolean|null>} true=favourited, false=not, null=blocked (prompt shown)
   */
  async add(vendorId) {
    return this._backendAdd(vendorId);
  },

  /**
   * Removes a vendor from favourites.
   * @returns {Promise<boolean|null>} true=removed, false=not, null=blocked (prompt shown)
   */
  async remove(vendorId) {
    return this._backendRemove(vendorId);
  },

  /**
   * Toggles the favourite state of a vendor.
   * @returns {Promise<boolean|null>} new favourited state, or null if blocked (prompt shown)
   */
  async toggle(vendorId) {
    if (this._ids.has(vendorId)) {
      const removed = await this._backendRemove(vendorId);
      if (removed === null) return null; // blocked — prompt already shown
      return false;                       // now not favourited
    }
    return this._backendAdd(vendorId);    // returns true if favourited now
  },

  // ─── Backend helpers ────────────────────────────────────────────

  async _backendAdd(vendorId) {
    if (!this.canUseBackend()) {
      this.promptBlocked();
      return null;
    }
    try {
      const res = await API.addFavourite(vendorId);
      if (res.ok && res.data && res.data.success) {
        this._ids.add(vendorId);
        this._loadedFor = this.authKey();
        return true;
      }
      if (res.status === 401) {
        this.promptLogin();
        return null;
      }
      if (res.status === 404) {
        this._ids.delete(vendorId); // vendor no longer available → not favourited
        return false;
      }
      Toast.error('Could not save favourite', (res.data && res.data.message) || 'Please try again.');
      return false;
    } catch {
      Toast.error('Network error', 'Please check your connection and try again.');
      return false;
    }
  },

  async _backendRemove(vendorId) {
    if (!this.canUseBackend()) {
      this.promptBlocked();
      return null;
    }
    try {
      const res = await API.removeFavourite(vendorId);
      if (res.ok && res.data && res.data.success) {
        this._ids.delete(vendorId);
        this._loadedFor = this.authKey();
        return true;
      }
      if (res.status === 401) {
        this.promptLogin();
        return null;
      }
      if (res.status === 404) {
        this._ids.delete(vendorId); // already not favourited
        return false;
      }
      Toast.error('Could not remove favourite', (res.data && res.data.message) || 'Please try again.');
      return false;
    } catch {
      Toast.error('Network error', 'Please check your connection and try again.');
      return false;
    }
  },

  _loadLocalFallback() {
    try {
      JSON.parse(localStorage.getItem(CONFIG.STORAGE_KEYS.FAVOURITES) || '[]')
        .forEach(id => this._ids.add(id));
    } catch {
      // Ignore malformed local data.
    }
  },

  promptBlocked() {
    const u = this.currentUser();
    if (u && u.role === 'VENDOR') {
      Toast.warning('Not available for business accounts', 'Vendor accounts cannot save favourites.');
      return;
    }
    this.promptLogin();
  },

  promptLogin() {
    Toast.warning('Log in required', 'Please log in to save your favourite vendors.');
    const onSubPage = window.location.pathname.includes('/pages/');
    setTimeout(() => {
      window.location.href = onSubPage ? 'login.html' : 'pages/login.html';
    }, 1600);
  },
};
