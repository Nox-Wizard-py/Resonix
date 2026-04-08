const express = require('express');
const { execFile } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');
const crypto = require('crypto');
const cache = require('../cache');

const router = express.Router();
const YT_DLP_BIN = process.env.YT_DLP_BINARY ?? 'yt-dlp';

// In-memory map: token → { filePath, expiresAt }
const pendingFiles = new Map();

/** Promisified execFile with stderr attached to error object. */
function run(cmd, args, opts = {}) {
  return new Promise((resolve, reject) => {
    execFile(cmd, args, opts, (err, stdout, stderr) => {
      if (err) { err.stderr = stderr; reject(err); }
      else resolve({ stdout, stderr });
    });
  });
}

function isSupportedUrl(url) {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.replace('www.', '');
    if (['youtube.com', 'youtu.be'].some((h) => host.includes(h))) return true;
    if (host.includes('instagram.com')) return !parsed.pathname.startsWith('/p/');
    return false;
  } catch { return false; }
}

router.post('/', async (req, res) => {
  const { url } = req.body ?? {};
  if (!url || typeof url !== 'string')
    return res.status(400).json({ error: 'Missing or invalid "url" in request body.' });

  if (!isSupportedUrl(url))
    return res.status(400).json({ error: 'Unsupported URL. Paste a YouTube or Instagram link.' });

  // Strip tracking params from Instagram share URLs
  let cleanUrl = url;
  if (cleanUrl.includes('instagram.com')) cleanUrl = cleanUrl.split('?')[0];

  const cacheKey = `download:${crypto.createHash('sha256').update(cleanUrl).digest('hex')}`;
  const cached = cache.get(cacheKey);
  if (cached) return res.json({ ...cached, cached: true });

  const tmpId = crypto.randomUUID();
  const tmpDir = os.tmpdir();
  const outputTemplate = path.join(tmpDir, `resonix_${tmpId}.%(ext)s`);
  const isInstagram = cleanUrl.includes('instagram.com');

  // ── Step 1: yt-dlp extraction ─────────────────────────────────────────────
  const ytArgs = [
    cleanUrl,
    '--format', 'bestaudio[ext=m4a]/bestaudio[ext=webm]/bestaudio',
    '--output', outputTemplate,
    '--no-playlist', '--no-warnings', '--no-check-certificate',
    ...(isInstagram
      ? ['--add-header', 'User-Agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122 Safari/537.36']
      : []),
  ];

  try {
    await run(YT_DLP_BIN, ytArgs, { timeout: 90_000 });
  } catch (ytErr) {
    const msg = ytErr.stderr?.trim() || ytErr.message;
    console.error('[download] yt-dlp error:', msg);
    return res.status(500).json({ error: `Failed to extract audio: ${msg}` });
  }

  const rawFiles = fs.readdirSync(tmpDir).filter((f) => f.startsWith(`resonix_${tmpId}`));
  if (!rawFiles.length)
    return res.status(500).json({ error: 'Audio extraction produced no output file.' });

  const rawFilename = rawFiles[0];
  const rawFilePath = path.join(tmpDir, rawFilename);
  const rawToken = `dl_${tmpId}_${rawFilename}`;
  const expiresAt = Date.now() + 15 * 60 * 1000;
  pendingFiles.set(rawToken, { filePath: rawFilePath, expiresAt });

  // ── Step 2: ffmpeg → WAV 16kHz mono PCM (loudnorm + silence removal) ──────
  const wavFilename = `recog_${tmpId}.wav`;
  const wavFilePath = path.join(tmpDir, wavFilename);
  let wavToken = rawToken;          // fallback: reuse raw m4a if ffmpeg fails
  let wavFilenameResult = rawFilename;

  const ffArgs = [
    '-y', '-i', rawFilePath,
    // Loudness normalization then silence trim at start/end
    '-af', 'loudnorm=I=-16:TP=-1.5:LRA=11,silenceremove=start_periods=1:start_silence=0.02:start_threshold=-60dB:stop_periods=-1:stop_silence=0.02:stop_threshold=-60dB',
    '-ar', '16000', '-ac', '1', '-c:a', 'pcm_s16le', '-f', 'wav',
    wavFilePath,
  ];

  try {
    await run('ffmpeg', ffArgs, { timeout: 60_000 });
    wavToken = `recog_${tmpId}_${wavFilename}`;
    wavFilenameResult = wavFilename;
    pendingFiles.set(wavToken, { filePath: wavFilePath, expiresAt });
    console.log(`[download] WAV ready: ${wavFilename}`);
  } catch (ffErr) {
    console.warn('[download] ffmpeg failed, using raw audio for recognition:', ffErr.message);
  }

  const result = {
    downloadUrl: `/api/download/file/${encodeURIComponent(rawToken)}`,
    filename: rawFilename,
    recognitionUrl: `/api/download/file/${encodeURIComponent(wavToken)}`,
    wavFilename: wavFilenameResult,
  };

  cache.set(cacheKey, result);
  return res.json(result);
});

router.get('/file/:token', (req, res) => {
  const entry = pendingFiles.get(req.params.token);
  if (!entry || Date.now() > entry.expiresAt) {
    pendingFiles.delete(req.params.token);
    return res.status(404).json({ error: 'Download link expired or not found.' });
  }
  res.download(entry.filePath, undefined, (err) => {
    if (err) console.error('[download] File transfer error:', err.message);
  });
});

module.exports = router;
