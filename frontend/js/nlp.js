/**
 * Maitri — NLP Review Analysis Module (Phase 13)
 *
 * Handles NLP analysis of review text and vendor insights.
 * All API calls go through the centralized api.js client.
 */

// ── Initialisation ─────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  // Initialize Navbar
  if (typeof Navbar !== 'undefined') Navbar.init();

  // Check authentication status
  const token = localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
  const analyzeBtn = document.getElementById('nlp-analyze-btn');
  const nlpTextarea = document.getElementById('nlp-textarea');

  // Enable/Disable analyze button based on auth and text input
  if (analyzeBtn && nlpTextarea) {
    // Initial state: disabled if no token or empty text
    updateAnalyzeButtonState();

    // Textarea input listener
    nlpTextarea.addEventListener('input', () => {
      updateAnalyzeButtonState();
    });

    // Analyze button click
    analyzeBtn.addEventListener('click', () => {
      performNLPAnalysis();
    });
  }
});

// ── Helper: Update analyze button state ──────────────────────────

function updateAnalyzeButtonState() {
  const analyzeBtn = document.getElementById('nlp-analyze-btn');
  const nlpTextarea = document.getElementById('nlp-textarea');

  if (!analyzeBtn || !nlpTextarea) return;

  const text = nlpTextarea.value.trim();
  const hasToken = !!localStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);

  if (analyzeBtn) {
    if (hasToken && text.length > 0) {
      analyzeBtn.disabled = false;
      analyzeBtn.classList.remove('btn--disabled');
    } else {
      analyzeBtn.disabled = true;
      analyzeBtn.classList.add('btn--disabled');
    }
  }
}

// ── Core: Perform NLP analysis ────────────────────────────────────

async function performNLPAnalysis() {
  const analyzeBtn = document.getElementById('nlp-analyze-btn');
  const nlpTextarea = document.getElementById('nlp-textarea');
  const nlpLoading = document.getElementById('nlp-loading');
  const nlpError = document.getElementById('nlp-error');
  const nlpResults = document.getElementById('nlp-results');
  const nlpEmpty = document.getElementById('nlp-empty');
  const sentimentDisplay = document.getElementById('sentiment-display');
  const sentimentBadge = document.getElementById('sentiment-badge');
  const sentimentConfidence = document.getElementById('sentiment-confidence');
  const keywordsList = document.getElementById('keywords-list');
  const aspectsGrid = document.getElementById('aspects-grid');
  const nlpCharCount = document.getElementById('nlp-char-count');
  const nlpWordCount = document.getElementById('nlp-word-count');

  if (!analyzeBtn || !nlpTextarea || !nlpLoading || !nlpError || !nlpResults || !sentimentDisplay) return;

  const text = nlpTextarea.value.trim();

  // Show loading state, hide results
  analyzeBtn.disabled = true;
  analyzeBtn.textContent = 'Analyzing…';
  nlpLoading.style.display = 'block';
  nlpError.style.display = 'none';
  nlpResults.style.display = 'none';
  nlpEmpty.style.display = 'none';

  try {
    // Call backend NLP analysis
    const result = await API.analyzeText(text, parseInt(document.getElementById('nlp-max-keywords').value) || 10);

    // Handle error response
    if (!result.ok) {
      throw new Error(result.data?.message || 'Analysis failed');
    }

    // Hide loading, show results
    nlpLoading.style.display = 'none';
    analyzeBtn.textContent = 'Analyze Text';

    if (!result.data) {
      throw new Error('No data returned from backend');
    }

    const data = result.data;

    // Display sentiment
    displaySentiment(data.sentiment, data.confidence);

    // Display keywords
    displayKeywords(data.keywords || []);

    // Display aspect sentiment
    displayAspectSentiment(data.aspects || []);

    // Display text info
    nlpCharCount.textContent = data.textLength || text.length;
    nlpWordCount.textContent = countWords(text) || 0;

    nlpResults.style.display = 'block';

  } catch (err) {
    // Show error state
    nlpLoading.style.display = 'none';
    analyzeBtn.disabled = false;
    analyzeBtn.textContent = 'Analyze Text';

    const errorMsg = err.message || 'Unable to analyze text. Please try again.';
    showToast('Analysis failed', errorMsg, 'error');

    // Display error UI
    if (nlpError) {
      nlpError.style.display = 'block';
      nlpError.textContent = errorMsg;
    }
  }
}

// ── Display: Sentiment ────────────────────────────────────────────

function displaySentiment(sentiment, confidence) {
  const sentimentBadge = document.getElementById('sentiment-badge');
  const sentimentConfidence = document.getElementById('sentiment-confidence');

  if (!sentimentBadge || !sentimentConfidence) return;

  // Set badge text and color
  const sentimentMap = {
    positive: { text: 'Positive', color: 'var(--color-positive)' },
    negative: { text: 'Negative', color: 'var(--color-negative)' },
    neutral: { text: 'Neutral', color: 'var(--color-neutral)' }
  };

  const s = sentimentMap[sentiment] || { text: sentiment || 'Unknown', color: 'var(--color-text)' };
  sentimentBadge.textContent = s.text;
  sentimentBadge.style.color = s.color;

  // Set confidence
  const confPercent = Math.round((confidence || 0) * 100);
  sentimentConfidence.textContent = `Confidence: ${confPercent}%`;

  // Set progress width
  const confidenceBar = sentimentConfidence.parentElement;
  if (confidenceBar && confidenceBar.style) {
    confidenceBar.style.width = `${confPercent}%`;
  }
}

// ── Display: Keywords ─────────────────────────────────────────────

function displayKeywords(keywords) {
  const keywordsList = document.getElementById('keywords-list');

  if (!keywordsList) return;

  if (!keywords || keywords.length === 0) {
    keywordsList.innerHTML = '<p class="empty-state">No keywords found.</p>';
    return;
  }

  keywordsList.innerHTML = keywords.slice(0, 10).map(kw => `
    <span class="keyword-chip">${escapeHtml(kw)}</span>
  `).join('');

  // Add CSS for keyword chips if not already present
  addKeywordChipStyle();
}

// ── Display: Aspect Sentiment ─────────────────────────────────────

function displayAspectSentiment(aspects) {
  const aspectsGrid = document.getElementById('aspects-grid');

  if (!aspectsGrid) return;

  if (!aspects || aspects.length === 0) {
    // Show all aspects as not detected
    aspectsGrid.innerHTML = `
      <div class="aspect-item">
        <span class="aspect-name">Food</span>
        <span class="aspect-sentiment">—</span>
        <span class="aspect-confidence">0%</span>
      </div>
      <div class="aspect-item">
        <span class="aspect-name">Pricing</span>
        <span class="aspect-sentiment">—</span>
        <span class="aspect-confidence">0%</span>
      </div>
      <div class="aspect-item">
        <span class="aspect-name">Delivery</span>
        <span class="aspect-sentiment">—</span>
        <span class="aspect-confidence">0%</span>
      </div>
      <div class="aspect-item">
        <span class="aspect-name">Quality</span>
        <span class="aspect-sentiment">—</span>
        <span class="aspect-confidence">0%</span>
      </div>
      <div class="aspect-item">
        <span class="aspect-name">Staff</span>
        <span class="aspect-sentiment">—</span>
        <span class="aspect-confidence">0%</span>
      </div>
    `;
    return;
  }

  // Build aspect items from returned data
  // Format: [{aspect: "food", sentiment: "positive", confidence: 0.8}]
  const aspectMap = {
    food: document.getElementById('aspect-food'),
    pricing: document.getElementById('aspect-pricing'),
    delivery: document.getElementById('aspect-delivery'),
    quality: document.getElementById('aspect-quality'),
    staff: document.getElementById('aspect-staff')
  };

  // Initialize all aspects as not detected
  Object.values(aspectMap).forEach(el => {
    if (el) {
      el.querySelector('.aspect-sentiment').textContent = '—';
      el.querySelector('.aspect-confidence').textContent = '0%';
    }
  });

  // Update detected aspects
  aspects.forEach(aspect => {
    const key = aspect.aspect?.toLowerCase();
    const el = aspectMap[key];
    if (el) {
      const sentimentText = aspect.sentiment && aspect.sentiment !== 'neutral' ? aspect.sentiment : 'Neutral';
      const confidencePercent = Math.round((aspect.confidence || 0) * 100);
      el.querySelector('.aspect-sentiment').textContent = sentimentText;
      el.querySelector('.aspect-confidence').textContent = `${confidencePercent}%`;
    }
  });
}

// ── Helper: Count words ───────────────────────────────────────────

function countWords(text) {
  if (!text) return 0;
  return text.trim().split(/\s+/).filter(w => w.length > 0).length;
}

// ── Utility: Escape HTML ──────────────────────────────────────────

function escapeHtml(text) {
  if (text === null || text === undefined) return '';
  const div = document.createElement('div');
  div.textContent = String(text);
  return div.innerHTML;
}

// ── Utility: Add keyword chip style ───────────────────────────────

function addKeywordChipStyle() {
  // Check if style already exists
  if (document.getElementById('keyword-chip-style')) return;

  const style = document.createElement('style');
  style.id = 'keyword-chip-style';
  style.textContent = `
    .keyword-chip {
      display: inline-block;
      padding: 0.2rem 0.5rem;
      background: var(--color-bg-subtle);
      border-radius: var(--radius-sm);
      font-size: var(--font-size-sm);
      margin: 0.1rem;
      white-space: nowrap;
      color: var(--color-text);
    }
  `;
  document.head.appendChild(style);
}