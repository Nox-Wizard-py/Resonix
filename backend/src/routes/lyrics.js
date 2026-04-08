const express = require('express');
const axios = require('axios');
const cache = require('../cache');

const router = express.Router();
const LRCLIB_SEARCH_URL = 'https://lrclib.net/api/search';
const LRCLIB_GET_URL    = 'https://lrclib.net/api/get';

/**
 * GET /api/lyrics?q=lyrics+text+here
 * Returns the best matched song from LRCLIB.
 */
router.get('/', async (req, res) => {
  const query = req.query.q?.trim();
  if (!query || query.length < 3) {
    return res.status(400).json({ error: 'Query too short. Provide at least a few lyrics words.' });
  }

  const cacheKey = `lyrics:${query.toLowerCase()}`;
  const cached = cache.get(cacheKey);
  if (cached) {
    return res.json({ ...cached, cached: true });
  }

  try {
    const response = await axios.get(LRCLIB_SEARCH_URL, {
      params: { track_name: query },
      timeout: 10000,
      headers: { 'User-Agent': 'Resonix/1.0 (music app)' },
    });

    let results = response.data;

    // Fallback: if track_name param returned nothing, try free-text q param
    if (!Array.isArray(results) || results.length === 0) {
      const fallback = await axios.get(LRCLIB_SEARCH_URL, {
        params: { q: query },
        timeout: 10000,
        headers: { 'User-Agent': 'Resonix/1.0 (music app)' },
      });
      results = fallback.data;
    }

    if (!Array.isArray(results) || results.length === 0) {
      return res.status(404).json({ error: 'No songs found matching those lyrics.' });
    }

    // Pick the result with the highest duration (usually the full studio track)
    const best = results.sort((a, b) => (b.duration ?? 0) - (a.duration ?? 0))[0];

    // Clean album name — discard if it looks like raw LRC/sync data
    const rawAlbum = best.albumName ?? null;
    const cleanAlbum = rawAlbum && !rawAlbum.includes('[') && !rawAlbum.includes('\r') ? rawAlbum : null;

    const result = {
      title: best.trackName,
      artist: best.artistName,
      album: cleanAlbum,
      duration: best.duration ?? null,
      youtubeSearchQuery: `${best.trackName} ${best.artistName}`,
    };

    cache.set(cacheKey, result);
    return res.json(result);

  } catch (err) {
    console.error('[lyrics]', err.message);
    return res.status(500).json({ error: 'Lyrics search service unavailable.' });
  }
});

module.exports = router;
