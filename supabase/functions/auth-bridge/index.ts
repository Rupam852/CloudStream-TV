import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

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
        return new Response('Missing session parameter', { status: 400 });
      }

      const { data: session, error } = await supabase
        .from('sessions')
        .select('*')
        .eq('id', sessionId)
        .maybeSingle();

      if (error || !session) {
        return new Response('Invalid or expired login session. Please close the login screen on your TV and open it again.', { status: 400 });
      }

      const useOpt2 = session.opt === '2';
      const clientId = (useOpt2 ? Deno.env.get('GOOGLE_CLIENT_ID_2') : Deno.env.get('GOOGLE_CLIENT_ID')) || (useOpt2 ? DEFAULT_CLIENT_ID_2 : DEFAULT_CLIENT_ID);
      
      // Dynamically resolve redirect URI from the request host
      const redirectUri = `${url.protocol}//${url.host}/functions/v1/auth-bridge/callback`;

      const googleAuthUrl = `https://accounts.google.com/o/oauth2/v2/auth?` +
        `client_id=${encodeURIComponent(clientId)}&` +
        `redirect_uri=${encodeURIComponent(redirectUri)}&` +
        `response_type=code&` +
        `scope=${encodeURIComponent('https://www.googleapis.com/auth/drive.readonly email')}&` +
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
        return new Response('Missing code or state parameter', { status: 400 });
      }

      const { data: session, error } = await supabase
        .from('sessions')
        .select('*')
        .eq('id', sessionId)
        .maybeSingle();

      if (error || !session) {
        return new Response('Session expired or invalid.', { status: 400 });
      }

      const useOpt2 = session.opt === '2';
      const clientId = (useOpt2 ? Deno.env.get('GOOGLE_CLIENT_ID_2') : Deno.env.get('GOOGLE_CLIENT_ID')) || (useOpt2 ? DEFAULT_CLIENT_ID_2 : DEFAULT_CLIENT_ID);
      const clientSecret = (useOpt2 ? Deno.env.get('GOOGLE_CLIENT_SECRET_2') : Deno.env.get('GOOGLE_CLIENT_SECRET')) || (useOpt2 ? DEFAULT_CLIENT_SECRET_2 : DEFAULT_CLIENT_SECRET);
      const redirectUri = `${url.protocol}//${url.host}/functions/v1/auth-bridge/callback`;

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

      // Fetch user's email address
      let email = null;
      try {
        const userInfoRes = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
          headers: { Authorization: `Bearer ${tokens.access_token}` }
        });
        if (userInfoRes.ok) {
          const userInfo = await userInfoRes.json();
          email = userInfo.email;
        }
      } catch (e) {
        console.error('Failed to retrieve user email', e);
      }

      // Save credentials & authorize session
      await supabase.from('sessions').update({
        status: 'authorized',
        access_token: tokens.access_token,
        refresh_token: tokens.refresh_token,
        expires_in: tokens.expires_in,
        email: email
      }).eq('id', sessionId);

      return new Response(`
        <div style="font-family: sans-serif; text-align: center; margin-top: 100px; padding: 20px;">
            <h1 style="color: #10B981;">✓ Authentication Successful!</h1>
            <p style="font-size: 18px; color: #4B5563;">You have successfully signed in with your Google Account.</p>
            <p style="font-size: 16px; color: #6B7280;">You can now close this tab and return to your TV screen.</p>
        </div>
      `, {
        headers: { ...corsHeaders, 'Content-Type': 'text/html' }
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
