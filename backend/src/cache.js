/**
 * In-memory URL + audio-hash cache for recognition results.
 * Prevents duplicate API calls for the same content.
 */

const cache = new Map();
const TTL = parseInt(process.env.CACHE_TTL_MS ?? '86400000', 10);

/**
 * Store a result keyed by a string (URL or content hash).
 * @param {string} key
 * @param {object} value
 */
function set(key, value) {
  cache.set(key, { value, expiresAt: Date.now() + TTL });
}

/**
 * Retrieve a cached result, or null if missing/expired.
 * @param {string} key
 * @returns {object|null}
 */
function get(key) {
  const entry = cache.get(key);
  if (!entry) return null;
  if (Date.now() > entry.expiresAt) {
    cache.delete(key);
    return null;
  }
  return entry.value;
}

/**
 * Check if a key is cached and still valid.
 * @param {string} key
 * @returns {boolean}
 */
function has(key) {
  return get(key) !== null;
}

module.exports = { get, set, has };
