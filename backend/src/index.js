require('dotenv').config();

const express = require('express');
const cors = require('cors');
const rateLimit = require('express-rate-limit');

const downloadRouter = require('./routes/download');
const lyricsRouter = require('./routes/lyrics');

// ── Sync extensions ───────────────────────────────────────────────────────────
const { createServer } = require('http');
const { WebSocketServer } = require('ws');
const { handleMessage, handleDisconnect } = require('./sync/syncHandler');

const app = express();
const PORT = process.env.PORT || 3000;

// ── Middleware ────────────────────────────────────────────────────────────────

app.use(cors());
app.use(express.json({ limit: '10mb' }));

const downloadLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 10,
  message: { error: 'Too many download requests. Please wait a moment.' },
});

// ── Routes ────────────────────────────────────────────────────────────────────

app.use('/api/download', downloadLimiter, downloadRouter);
app.use('/api/lyrics', lyricsRouter);

app.get('/health', (_, res) => res.json({ status: 'ok', service: 'resonix-backend' }));

// ── Error Handler ─────────────────────────────────────────────────────────────

app.use((err, req, res, _next) => {
  console.error('[global error]', err);
  res.status(500).json({ error: 'An unexpected server error occurred.' });
});

// ── Start ─────────────────────────────────────────────────────────────────────

const server = createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

wss.on('connection', (ws) => {
    const clientId = require('crypto').randomUUID();
    console.log(`[sync] client connected: ${clientId}`);

    ws.on('message', (data) => handleMessage(clientId, data.toString(), ws));
    ws.on('close', () => handleDisconnect(clientId));
    ws.on('error', (err) => {
        console.error(`[sync] error (${clientId}):`, err.message);
        handleDisconnect(clientId);
    });
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`✅ Resonix backend running on port ${PORT}`);
    console.log(`   /api/download   — yt-dlp audio extraction`);
    console.log(`   /api/lyrics     — LRCLIB lyrics search`);
    console.log(`   /ws             — ResonixSync WebSocket`);
});
