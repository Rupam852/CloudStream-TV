const express = require('express');
const axios = require('axios');
const crypto = require('crypto');
const app = express();
const PORT = process.env.PORT || 3000;

// In-memory store for login sessions
// maps sessionId -> { status: 'pending'|'authorized', tokens: null|{ access_token, refresh_token, expires_in, email } }
const sessions = new Map();

// Automatic cleanup: Delete sessions older than 10 minutes to prevent memory leak
setInterval(() => {
    const now = Date.now();
    for (const [id, session] of sessions.entries()) {
        if (now - session.createdAt > 600000) {
            sessions.delete(id);
        }
    }
}, 60000);

// Basic home page check
app.get('/', (req, res) => {
    res.send('<h1>CloudStream TV Auth Server</h1><p>Running successfully!</p>');
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
    const sessionId = req.query.session;
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
    const sessionId = req.query.state;

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
    const sessionId = req.query.session;
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
