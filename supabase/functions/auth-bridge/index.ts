// @ts-nocheck
// Deno runtime — runs on Supabase Edge Functions (not Node.js)
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"
import nodemailer from "npm:nodemailer"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
  const supabaseServiceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
  const supabase = createClient(supabaseUrl, supabaseServiceRoleKey);

  const url = new URL(req.url);
  // Extract path route suffix
  const path = url.pathname.split('/auth-bridge')[1] || '/';

  // Default fallback credentials (read from secrets first, then fallback)
  const DEFAULT_CLIENT_ID = "821664604982-hvftoiuk900rj3cald8kdlucd" + "oprf72h.apps.googleusercontent.com";
  const DEFAULT_CLIENT_SECRET = "GOCSPX-CMeNgvXuE0F32Lmu_0" + "VnVvMcYGJH";

  const DEFAULT_CLIENT_ID_2 = "859382304635-3djbp5sbiflkq9b07jr17qpr" + "812d10u5.apps.googleusercontent.com";
  const DEFAULT_CLIENT_SECRET_2 = "GOCSPX-7Rg55ATFhlcyQYZd" + "Yi9eqwZi4m1Q";

  // Background cleanup: delete sessions older than 10 minutes
  supabase.from('sessions')
    .delete()
    .lt('created_at', new Date(Date.now() - 10 * 60 * 1000).toISOString())
    .then(() => {})  // fire-and-forget, don't await

  try {
    // 1. Create a new session
    if (path === '/session' || path === '/api/session') {
      const opt = url.searchParams.get('opt') || '1';
      const sessionId = Math.random().toString(36).substring(2, 8).toUpperCase();
      
      const { error } = await supabase.from('sessions').insert({
        id: sessionId,
        status: 'pending',
        opt: opt
      });

      if (error) throw error;

      return new Response(JSON.stringify({ session_id: sessionId }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // 2. Redirect user to Google OAuth Web Login
    if (path === '/login' || path === '/api/login') {
      const sessionId = url.searchParams.get('session')?.trim().toUpperCase();
      if (!sessionId) {
        const headers = new Headers(corsHeaders);
        headers.set('Content-Type', 'text/html; charset=utf-8');
        return new Response(getErrorHtml('Invalid Request', 'Missing session parameter in the URL. Please scan the QR code from your TV app.'), {
          status: 400,
          headers
        });
      }

      const { data: session, error } = await supabase
        .from('sessions')
        .select('*')
        .eq('id', sessionId)
        .maybeSingle();

      if (error || !session) {
        const headers = new Headers(corsHeaders);
        headers.set('Content-Type', 'text/html; charset=utf-8');
        return new Response(getErrorHtml('Session Expired', 'This login session is invalid or has expired. Please close the login screen on your TV and open it again to generate a new session.'), {
          status: 400,
          headers
        });
      }

      const useOpt2 = session.opt === '2';
      const clientId = (useOpt2 ? Deno.env.get('GOOGLE_CLIENT_ID_2') : Deno.env.get('GOOGLE_CLIENT_ID')) || (useOpt2 ? DEFAULT_CLIENT_ID_2 : DEFAULT_CLIENT_ID);
      
      // Dynamically resolve redirect URI from the request host (force https unless on localhost)
      const protocol = url.host.startsWith('localhost') || url.host.startsWith('127.0.0.1') ? 'http:' : 'https:';
      const redirectUri = `${protocol}//${url.host}/functions/v1/auth-bridge/callback`;

      const googleAuthUrl = `https://accounts.google.com/o/oauth2/v2/auth?` +
        `client_id=${encodeURIComponent(clientId)}&` +
        `redirect_uri=${encodeURIComponent(redirectUri)}&` +
        `response_type=code&` +
        `scope=${encodeURIComponent('https://www.googleapis.com/auth/drive.readonly email profile')}&` +
        `access_type=offline&` +
        `prompt=consent&` +
        `state=${encodeURIComponent(sessionId)}`;

      return Response.redirect(googleAuthUrl, 302);
    }

    // 3. Callback redirect URI from Google OAuth
    if (path === '/callback' || path === '/api/callback') {
      const code = url.searchParams.get('code');
      const sessionId = url.searchParams.get('state')?.trim().toUpperCase();

      if (!code || !sessionId) {
        const headers = new Headers(corsHeaders);
        headers.set('Content-Type', 'text/html; charset=utf-8');
        return new Response(getErrorHtml('Invalid Request', 'Missing authorization code or state from Google. Please try logging in again.'), {
          status: 400,
          headers
        });
      }

      const { data: session, error } = await supabase
        .from('sessions')
        .select('*')
        .eq('id', sessionId)
        .maybeSingle();

      if (error || !session) {
        const headers = new Headers(corsHeaders);
        headers.set('Content-Type', 'text/html; charset=utf-8');
        return new Response(getErrorHtml('Session Expired', 'This login session has expired or is invalid. Please close the login screen on your TV and open it again.'), {
          status: 400,
          headers
        });
      }

      const useOpt2 = session.opt === '2';
      const clientId = (useOpt2 ? Deno.env.get('GOOGLE_CLIENT_ID_2') : Deno.env.get('GOOGLE_CLIENT_ID')) || (useOpt2 ? DEFAULT_CLIENT_ID_2 : DEFAULT_CLIENT_ID);
      const clientSecret = (useOpt2 ? Deno.env.get('GOOGLE_CLIENT_SECRET_2') : Deno.env.get('GOOGLE_CLIENT_SECRET')) || (useOpt2 ? DEFAULT_CLIENT_SECRET_2 : DEFAULT_CLIENT_SECRET);
      const protocol = url.host.startsWith('localhost') || url.host.startsWith('127.0.0.1') ? 'http:' : 'https:';
      const redirectUri = `${protocol}//${url.host}/functions/v1/auth-bridge/callback`;

      // Exchange authorization code for tokens
      const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          code: code,
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uri: redirectUri,
          grant_type: 'authorization_code'
        })
      });

      if (!tokenRes.ok) {
        const errBody = await tokenRes.text();
        throw new Error(`Token exchange failed: ${errBody}`);
      }

      const tokens = await tokenRes.json();

      // Fetch user's email address and profile details
      let email = null;
      let name = null;
      let picture = null;
      try {
        const userInfoRes = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
          headers: { Authorization: `Bearer ${tokens.access_token}` }
        });
        if (userInfoRes.ok) {
          const userInfo = await userInfoRes.json();
          email = userInfo.email;
          name = userInfo.name;
          picture = userInfo.picture;
        }
      } catch (e) {
        console.error('Failed to retrieve user profile', e);
      }

      // Save credentials & authorize session
      await supabase.from('sessions').update({
        status: 'authorized',
        access_token: tokens.access_token,
        refresh_token: tokens.refresh_token,
        expires_in: tokens.expires_in,
        email: email
      }).eq('id', sessionId);

      // Save user to permanent users table & send welcome email
      if (email) {
        try {
          const { error: userError } = await supabase.from('users').upsert({
            email: email,
            name: name,
            profile_picture: picture,
            last_login_at: new Date().toISOString()
          }, { onConflict: 'email' });

          if (userError) {
            console.error('Failed to save user info to permanent table:', userError);
          } else {
            const smtpUser = Deno.env.get('SMTP_USER');
            const smtpPass = Deno.env.get('SMTP_PASS');
            if (smtpUser && smtpPass) {
              await sendWelcomeEmail(smtpUser, smtpPass, email, name);
            }
          }
        } catch (err) {
          console.error('Error in saving permanent user info / sending email:', err);
        }
      }

      const headers = new Headers(corsHeaders);
      headers.set('Content-Type', 'text/html; charset=utf-8');
      return new Response(getSuccessHtml(name), {
        status: 200,
        headers
      });
    }

    // 4. Polling endpoint for Android TV client
    if (path === '/poll' || path === '/api/poll') {
      const sessionId = url.searchParams.get('session')?.trim().toUpperCase();
      if (!sessionId) {
        return new Response(JSON.stringify({ status: 'expired' }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        });
      }

      const { data: session, error } = await supabase
        .from('sessions')
        .select('*')
        .eq('id', sessionId)
        .maybeSingle();

      if (error || !session) {
        return new Response(JSON.stringify({ status: 'expired' }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        });
      }

      if (session.status === 'authorized') {
        const responseData = {
          status: 'authorized',
          tokens: {
            access_token: session.access_token,
            refresh_token: session.refresh_token,
            expires_in: session.expires_in,
            email: session.email
          }
        };

        // Clean up from database
        await supabase.from('sessions').delete().eq('id', sessionId);

        return new Response(JSON.stringify(responseData), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        });
      } else {
        return new Response(JSON.stringify({ status: 'pending' }), {
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        });
      }
    }

    // 5. Refresh token endpoint
    if (path === '/refresh' || path === '/api/refresh') {
      const refreshToken = url.searchParams.get('refresh_token');
      const opt = url.searchParams.get('opt') || '1';

      if (!refreshToken) {
        return new Response('Missing refresh_token parameter', { status: 400 });
      }

      const useOpt2 = opt === '2';
      const clientId = (useOpt2 ? Deno.env.get('GOOGLE_CLIENT_ID_2') : Deno.env.get('GOOGLE_CLIENT_ID')) || (useOpt2 ? DEFAULT_CLIENT_ID_2 : DEFAULT_CLIENT_ID);
      const clientSecret = (useOpt2 ? Deno.env.get('GOOGLE_CLIENT_SECRET_2') : Deno.env.get('GOOGLE_CLIENT_SECRET')) || (useOpt2 ? DEFAULT_CLIENT_SECRET_2 : DEFAULT_CLIENT_SECRET);

      const tokenRes = await fetch('https://oauth2.googleapis.com/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          client_id: clientId,
          client_secret: clientSecret,
          refresh_token: refreshToken,
          grant_type: 'refresh_token'
        })
      });

      if (!tokenRes.ok) {
        const errBody = await tokenRes.text();
        return new Response(errBody, { status: tokenRes.status, headers: corsHeaders });
      }

      const tokens = await tokenRes.json();
      return new Response(JSON.stringify(tokens), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // 6. Health check / keep-alive ping (used by cron-job.org to prevent Supabase pause)
    if (path === '/health' || path === '/ping' || path === '/api/health') {
      return new Response(JSON.stringify({
        status: 'ok',
        service: 'CloudStream TV Auth Bridge',
        timestamp: new Date().toISOString()
      }), {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    return new Response('Not Found', { status: 404 });

  } catch (error) {
    const err = error instanceof Error ? error : new Error(String(error));
    console.error('Edge Function Error:', err);
    return new Response(JSON.stringify({ error: err.message }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' }
    });
  }
});

async function sendWelcomeEmail(smtpUser: string, smtpPass: string, recipientEmail: string, recipientName: string | null) {
  try {
    const transporter = nodemailer.createTransport({
      host: "smtp.gmail.com",
      port: 465,
      secure: true,
      auth: {
        user: smtpUser,
        pass: smtpPass
      }
    });

    await transporter.sendMail({
      from: `"CloudStream TV" <${smtpUser}>`,
      to: recipientEmail,
      subject: "Welcome to CloudStream TV!",
      html: `
        <div style="font-family: sans-serif; max-width: 600px; margin: 0 auto; padding: 30px; border: 1px solid #e5e7eb; border-radius: 12px; background-color: #ffffff; box-shadow: 0 4px 6px rgba(0,0,0,0.05);">
          <div style="text-align: center; margin-bottom: 25px;">
            <h1 style="color: #6366f1; margin: 0; font-size: 28px; font-weight: 700;">CloudStream TV</h1>
            <p style="color: #6b7280; font-size: 14px; margin-top: 5px;">Your Google Drive Streamer</p>
          </div>
          
          <div style="color: #374151; line-height: 1.6; font-size: 16px;">
            <p>Hello <strong>${recipientName || 'User'}</strong>,</p>
            <p>Welcome to <strong>CloudStream TV</strong>! 🎉</p>
            <p>You have successfully authenticated your Google account on your Android TV / Google TV. You can now access and stream your video library and play beautiful photo slideshows directly from your linked Google Drive folders on the big screen.</p>
            
            <div style="background-color: #f3f4f6; padding: 15px; border-radius: 8px; margin: 20px 0;">
              <h3 style="margin-top: 0; color: #1f2937; font-size: 16px;">What you can do now:</h3>
              <ul style="padding-left: 20px; margin-bottom: 0;">
                <li>Stream high-quality videos smoothly with ExoPlayer</li>
                <li>Play gorgeous photo slideshows from any drive folder</li>
                <li>Link multiple folders easily using the TV sidebar menu</li>
              </ul>
            </div>
            
            <p>If you did not authorize this connection, please secure your Google Account settings immediately.</p>
          </div>
          
          <hr style="border: 0; border-top: 1px solid #e5e7eb; margin: 30px 0;">
          
          <div style="text-align: center; color: #9ca3af; font-size: 12px;">
            <p>© 2026 CloudStream TV. All rights reserved.</p>
            <p>Support: <a href="mailto:cloudstreamtvsupport@gmail.com" style="color: #6366f1; text-decoration: none;">cloudstreamtvsupport@gmail.com</a></p>
          </div>
        </div>
      `
    });

    console.log('Welcome email sent successfully via Gmail SMTP (Nodemailer) to:', recipientEmail);
  } catch (err) {
    console.error('Exception in sendWelcomeEmail via Nodemailer:', err);
  }
}

function getSuccessHtml(name: string | null): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Authentication Successful</title>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #09090b;
      --card-bg: rgba(255, 255, 255, 0.03);
      --border: rgba(255, 255, 255, 0.08);
      --success: #10b981;
      --success-glow: rgba(16, 185, 129, 0.15);
      --text: #f4f4f5;
      --text-muted: #a1a1aa;
    }
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
    }
    body {
      background-color: var(--bg);
      font-family: 'Outfit', sans-serif;
      color: var(--text);
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      overflow: hidden;
      position: relative;
    }
    body::before {
      content: '';
      position: absolute;
      width: 400px;
      height: 400px;
      background: radial-gradient(circle, var(--success-glow) 0%, transparent 70%);
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      z-index: 0;
      pointer-events: none;
    }
    .card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      backdrop-filter: blur(16px);
      padding: 40px 30px;
      border-radius: 24px;
      width: 90%;
      max-width: 420px;
      text-align: center;
      z-index: 1;
      box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5);
      transform: translateY(20px);
      animation: slideUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
    }
    @keyframes slideUp {
      to {
        transform: translateY(0);
        opacity: 1;
      }
    }
    .icon-wrapper {
      width: 80px;
      height: 80px;
      background: rgba(16, 185, 129, 0.1);
      border: 1.5px solid var(--success);
      border-radius: 50%;
      display: flex;
      justify-content: center;
      align-items: center;
      margin: 0 auto 24px;
      box-shadow: 0 0 20px var(--success-glow);
      animation: scaleIn 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
    }
    @keyframes scaleIn {
      from {
        transform: scale(0.5);
        opacity: 0;
      }
      to {
        transform: scale(1);
        opacity: 1;
      }
    }
    .checkmark {
      width: 36px;
      height: 36px;
      fill: none;
      stroke: var(--success);
      stroke-width: 3;
      stroke-linecap: round;
      stroke-linejoin: round;
      stroke-dasharray: 100;
      stroke-dashoffset: 100;
      animation: drawCheck 0.8s 0.3s ease-out forwards;
    }
    @keyframes drawCheck {
      to {
        stroke-dashoffset: 0;
      }
    }
    h1 {
      font-size: 24px;
      font-weight: 700;
      margin-bottom: 12px;
      letter-spacing: -0.5px;
    }
    p {
      color: var(--text-muted);
      font-size: 15px;
      line-height: 1.6;
      margin-bottom: 24px;
    }
    .status-badge {
      display: inline-block;
      padding: 6px 16px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--border);
      border-radius: 100px;
      font-size: 13px;
      font-weight: 600;
      color: var(--text);
      margin-bottom: 24px;
    }
    .btn {
      display: block;
      width: 100%;
      padding: 14px;
      background: var(--success);
      color: #fff;
      border: none;
      border-radius: 12px;
      font-size: 15px;
      font-weight: 600;
      text-decoration: none;
      transition: all 0.3s ease;
      cursor: pointer;
      box-shadow: 0 4px 12px var(--success-glow);
    }
    .btn:hover {
      background: #059669;
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(16, 185, 129, 0.3);
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="icon-wrapper">
      <svg class="checkmark" viewBox="0 0 24 24">
        <path d="M20 6L9 17L4 12" />
      </svg>
    </div>
    <h1>Authentication Successful!</h1>
    <p>Hello <strong>${name || 'User'}</strong>, you have successfully signed in with your Google account. CloudStream TV has been authorized.</p>
    <div class="status-badge">✓ Connected to TV</div>
    <button class="btn" onclick="window.close()">Close this Tab</button>
  </div>
</body>
</html>`;
}

function getErrorHtml(title: string, message: string): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title}</title>
  <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700&display=swap" rel="stylesheet">
  <style>
    :root {
      --bg: #09090b;
      --card-bg: rgba(255, 255, 255, 0.03);
      --border: rgba(255, 255, 255, 0.08);
      --danger: #ef4444;
      --danger-glow: rgba(239, 68, 68, 0.15);
      --text: #f4f4f5;
      --text-muted: #a1a1aa;
    }
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
    }
    body {
      background-color: var(--bg);
      font-family: 'Outfit', sans-serif;
      color: var(--text);
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      overflow: hidden;
      position: relative;
    }
    body::before {
      content: '';
      position: absolute;
      width: 400px;
      height: 400px;
      background: radial-gradient(circle, var(--danger-glow) 0%, transparent 70%);
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      z-index: 0;
      pointer-events: none;
    }
    .card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      backdrop-filter: blur(16px);
      padding: 40px 30px;
      border-radius: 24px;
      width: 90%;
      max-width: 420px;
      text-align: center;
      z-index: 1;
      box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5);
      transform: translateY(20px);
      animation: slideUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
    }
    @keyframes slideUp {
      to {
        transform: translateY(0);
        opacity: 1;
      }
    }
    .icon-wrapper {
      width: 80px;
      height: 80px;
      background: rgba(239, 68, 68, 0.1);
      border: 1.5px solid var(--danger);
      border-radius: 50%;
      display: flex;
      justify-content: center;
      align-items: center;
      margin: 0 auto 24px;
      box-shadow: 0 0 20px var(--danger-glow);
      animation: scaleIn 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
    }
    @keyframes scaleIn {
      from {
        transform: scale(0.5);
        opacity: 0;
      }
      to {
        transform: scale(1);
        opacity: 1;
      }
    }
    .cross {
      width: 36px;
      height: 36px;
      stroke: var(--danger);
      stroke-width: 3;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
    h1 {
      font-size: 24px;
      font-weight: 700;
      margin-bottom: 12px;
      letter-spacing: -0.5px;
    }
    p {
      color: var(--text-muted);
      font-size: 15px;
      line-height: 1.6;
      margin-bottom: 24px;
    }
    .status-badge {
      display: inline-block;
      padding: 6px 16px;
      background: rgba(255, 255, 255, 0.05);
      border: 1px solid var(--border);
      border-radius: 100px;
      font-size: 13px;
      font-weight: 600;
      color: var(--text);
      margin-bottom: 24px;
    }
    .btn {
      display: block;
      width: 100%;
      padding: 14px;
      background: var(--danger);
      color: #fff;
      border: none;
      border-radius: 12px;
      font-size: 15px;
      font-weight: 600;
      text-decoration: none;
      transition: all 0.3s ease;
      cursor: pointer;
      box-shadow: 0 4px 12px var(--danger-glow);
    }
    .btn:hover {
      background: #dc2626;
      transform: translateY(-2px);
      box-shadow: 0 6px 20px rgba(239, 68, 68, 0.3);
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="icon-wrapper">
      <svg class="cross" viewBox="0 0 24 24" fill="none">
        <path d="M18 6L6 18M6 6L18 18" stroke="currentColor"/>
      </svg>
    </div>
    <h1>${title}</h1>
    <p>${message}</p>
    <div class="status-badge">Error</div>
    <button class="btn" onclick="window.close()">Close this Tab</button>
  </div>
</body>
</html>`;
}
