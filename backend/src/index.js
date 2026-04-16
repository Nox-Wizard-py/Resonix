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

// Maps server-assigned connectionId → app-provided userId
const connectionToUserId = new Map();

// Allow syncHandler to register the resolved userId per connection
function registerUserId(connectionId, userId) {
    connectionToUserId.set(connectionId, userId);
}

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
    const connectionId = require('crypto').randomUUID();
    console.log(`[sync] client connected: ${connectionId}`);

    ws.on('message', (data) => {
        const raw = data.toString();
        // Peek at userId before full dispatch so we can register the mapping
        try {
            const msg = JSON.parse(raw);
            if (msg.type === 'join_room' && msg.userId) {
                connectionToUserId.set(connectionId, msg.userId);
            }
        } catch {}
        const resolvedId = connectionToUserId.get(connectionId) || connectionId;
        handleMessage(resolvedId, raw, ws);
    });

    ws.on('close', () => {
        const resolvedId = connectionToUserId.get(connectionId) || connectionId;
        handleDisconnect(resolvedId);
        connectionToUserId.delete(connectionId);
    });
    
    ws.on('error', (err) => {
        console.error(`[sync] error (${connectionId}):`, err.message);
        const resolvedId = connectionToUserId.get(connectionId) || connectionId;
        handleDisconnect(resolvedId);
        connectionToUserId.delete(connectionId);
    });
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`✅ Resonix backend running on port ${PORT}`);
    console.log(`   /api/download   — yt-dlp audio extraction`);
    console.log(`   /api/lyrics     — LRCLIB lyrics search`);
    console.log(`   /ws             — ResonixSync WebSocket`);
});
