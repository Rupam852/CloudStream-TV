const express = require('express');
const axios = require('axios');
const crypto = require('crypto');
const app = express();
const PORT = process.env.PORT || 3000;

// In-memory store for login sessions
// maps sessionId -> { status: 'pending'|'authorized', tokens: null|{ access_token, refresh_token, expires_in, email } }
const sessions = new Map();

// Automatic cleanup: Delete sessions older than 5 minutes to prevent memory leak
setInterval(() => {
    const now = Date.now();
    for (const [id, session] of sessions.entries()) {
        if (now - session.createdAt > 300000) { // 5 minutes threshold
            sessions.delete(id);
        }
    }
}, 60000);

// Basic home page check with session code entry form
app.get('/', (req, res) => {
    res.send(`
        <!DOCTYPE html>
        <html>
        <head>
            <title>CloudStream TV - Authenticate</title>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    background: #0f172a;
                    color: #f8fafc;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    height: 100vh;
                    margin: 0;
                    padding: 20px;
                    box-sizing: border-box;
                }
                .card {
                    background: #1e293b;
                    border-radius: 16px;
                    padding: 32px;
                    width: 100%;
                    max-width: 400px;
                    box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.3);
                    text-align: center;
                    border: 1px solid #334155;
                }
                h1 {
                    color: #06b6d4;
                    font-size: 28px;
                    margin: 0 0 12px 0;
                }
                p {
                    color: #94a3b8;
                    font-size: 15px;
                    margin: 0 0 24px 0;
                    line-height: 1.6;
                }
                input {
                    width: 100%;
                    padding: 14px 16px;
                    border-radius: 8px;
                    border: 1px solid #475569;
                    background: #0f172a;
                    color: #f8fafc;
                    font-size: 20px;
                    text-align: center;
                    letter-spacing: 2px;
                    font-weight: bold;
                    box-sizing: border-box;
                    margin-bottom: 20px;
                    text-transform: uppercase;
                }
                input:focus {
                    outline: none;
                    border-color: #06b6d4;
                    box-shadow: 0 0 0 2px rgba(6, 182, 212, 0.2);
                }
                button {
                    width: 100%;
                    padding: 14px;
                    border-radius: 8px;
                    border: none;
                    background: #06b6d4;
                    color: #0f172a;
                    font-size: 16px;
                    font-weight: bold;
                    cursor: pointer;
                    transition: background 0.2s;
                }
                button:hover {
                    background: #0891b2;
                }
            </style>
        </head>
        <body>
            <div class="card">
                <h1>CloudStream TV</h1>
                <p>Enter the 6-character session code shown on your TV screen to authenticate with Google.</p>
                <form action="/api/login" method="get">
                    <input type="text" name="session" placeholder="E.G. E0127B" required maxlength="12" autocomplete="off">
                    <button type="submit">Connect with Google</button>
                </form>
            </div>
        </body>
        </html>
    `);
});

// Endpoint 1: TV requests a new Session ID
app.get('/api/session', (req, res) => {
    // Generate a clean 6-character unique session code (e.g. 'A2F89B')
    const sessionId = crypto.randomBytes(3).toString('hex').toUpperCase();
    sessions.set(sessionId, {
        status: 'pending',
        tokens: null,
        createdAt: Date.now()
    });
    console.log(`[Session Created] ID: ${sessionId}`);
    res.json({ session_id: sessionId });
});

// Endpoint 2: Phone scans QR code and hits this to redirect to Google
app.get('/api/login', (req, res) => {
    let sessionId = req.query.session;
    if (sessionId && typeof sessionId === 'string') {
        sessionId = sessionId.split(/[?&]/)[0].trim().toUpperCase();
    }
    if (!sessionId || !sessions.has(sessionId)) {
        return res.status(400).send('Invalid or expired login session. Please close the login screen on your TV and open it again.');
    }

    const clientId = process.env.GOOGLE_CLIENT_ID;
    const redirectUri = process.env.GOOGLE_REDIRECT_URI;

    if (!clientId || !redirectUri) {
        return res.status(500).send('Server configuration error: GOOGLE_CLIENT_ID or GOOGLE_REDIRECT_URI env variables are missing.');
    }

    // Direct user to Google's standard OAuth2 Login Web page
    const googleAuthUrl = `https://accounts.google.com/o/oauth2/v2/auth?` +
        `client_id=${encodeURIComponent(clientId)}&` +
        `redirect_uri=${encodeURIComponent(redirectUri)}&` +
        `response_type=code&` +
        `scope=${encodeURIComponent('https://www.googleapis.com/auth/drive.readonly email')}&` +
        `access_type=offline&` +
        `prompt=consent&` +
        `state=${encodeURIComponent(sessionId)}`; // Pass session ID as state so Google redirects it back

    res.redirect(googleAuthUrl);
});

// Endpoint 3: Google redirect callback page
app.get('/api/callback', async (req, res) => {
    const code = req.query.code;
    let sessionId = req.query.state;
    if (sessionId && typeof sessionId === 'string') {
        sessionId = sessionId.trim().toUpperCase();
    }

    if (!code || !sessionId || !sessions.has(sessionId)) {
        return res.status(400).send('Authentication expired or invalid request parameters.');
    }

    const clientId = process.env.GOOGLE_CLIENT_ID;
    const clientSecret = process.env.GOOGLE_CLIENT_SECRET;
    const redirectUri = process.env.GOOGLE_REDIRECT_URI;

    try {
        // Exchange authorization code for OAuth tokens
        const tokenResponse = await axios.post('https://oauth2.googleapis.com/token', {
            code: code,
            client_id: clientId,
            client_secret: clientSecret,
            redirect_uri: redirectUri,
            grant_type: 'authorization_code'
        });

        const tokens = tokenResponse.data;

        // Fetch user's email address
        let email = null;
        try {
            const userInfo = await axios.get('https://www.googleapis.com/oauth2/v3/userinfo', {
                headers: { Authorization: `Bearer ${tokens.access_token}` }
            });
            email = userInfo.data.email;
        } catch (e) {
            console.error('Failed to retrieve user email', e.message);
        }

        // Save credentials to session
        const session = sessions.get(sessionId);
        session.status = 'authorized';
        session.tokens = {
            access_token: tokens.access_token,
            refresh_token: tokens.refresh_token, // Saved refresh token for TV client long-term sessions
            expires_in: tokens.expires_in,
            email: email
        };
        sessions.set(sessionId, session);

        console.log(`[Auth Success] Session ID: ${sessionId} for user: ${email}`);

        res.send(`
            <div style="font-family: sans-serif; text-align: center; margin-top: 100px; padding: 20px;">
                <h1 style="color: #10B981;">✓ Authentication Successful!</h1>
                <p style="font-size: 18px; color: #4B5563;">You have successfully signed in with your Google Account.</p>
                <p style="font-size: 16px; color: #6B7280;">You can now close this tab and return to your TV screen.</p>
            </div>
        `);
    } catch (error) {
        console.error('Failed to exchange token with Google', error.response ? error.response.data : error.message);
        res.status(500).send(`
            <div style="font-family: sans-serif; text-align: center; margin-top: 100px; padding: 20px;">
                <h1 style="color: #EF4444;">✗ Authentication Failed</h1>
                <p style="font-size: 16px; color: #4B5563;">Could not verify code with Google. Please retry from your TV.</p>
            </div>
        `);
    }
});

// Endpoint 4: TV app polls this endpoint
app.get('/api/poll', (req, res) => {
    let sessionId = req.query.session;
    if (sessionId && typeof sessionId === 'string') {
        sessionId = sessionId.split(/[?&]/)[0].trim().toUpperCase();
    }
    if (!sessionId || !sessions.has(sessionId)) {
        return res.json({ status: 'expired' });
    }

    const session = sessions.get(sessionId);
    if (session.status === 'authorized') {
        res.json({
            status: 'authorized',
            tokens: session.tokens
        });
        // Remove session immediately after successful delivery
        sessions.delete(sessionId);
    } else {
        res.json({ status: 'pending' });
    }
});

app.listen(PORT, () => {
    console.log(`Auth Bridge Server is running on port ${PORT}`);
});
