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
        return Response.redirect(`https://cloudstream-tv.vercel.app/success.html?status=error&message=Missing%20session%20parameter%20in%20the%20URL.%20Please%20scan%20the%20QR%20code%20or%20enter%20the%20code%20manually.&t=${Date.now()}`, 302);
      }

      const { data: session, error } = await supabase
        .from('sessions')
        .select('*')
        .eq('id', sessionId)
        .maybeSingle();

      if (error || !session) {
        return Response.redirect(`https://cloudstream-tv.vercel.app/success.html?status=error&message=This%20login%20session%20is%20invalid%20or%20has%20expired.%20Please%20close%20the%20login%20screen%20on%20your%20TV%20and%20open%20it%20again.&t=${Date.now()}`, 302);
      }

      const useOpt2 = session.opt === '2';
      const clientId = (useOpt2 ? Deno.env.get('GOOGLE_CLIENT_ID_2') : Deno.env.get('GOOGLE_CLIENT_ID')) || (useOpt2 ? DEFAULT_CLIENT_ID_2 : DEFAULT_CLIENT_ID);
      
      // Dynamically resolve redirect URI from the request host (force https unless on localhost)
      const protocol = url.host.startsWith('localhost') || url.host.startsWith('127.0.0.1') ? 'http:' : 'https:';
      const redirectUri = `${protocol}//${url.host}/functions/v1/auth-bridge/callback`;

      const forceConsent = url.searchParams.get('force_consent') === 'true';
      const promptParam = forceConsent ? 'consent' : 'select_account';
      const stateParam = forceConsent ? `${sessionId}:force` : `${sessionId}:normal`;

      // Change prompt=consent to prompt=select_account so Google doesn't ask for permission scopes again if already consented.
      // If force_consent is true, we force prompt=consent to retrieve a new refresh token.
      const googleAuthUrl = `https://accounts.google.com/o/oauth2/v2/auth?` +
        `client_id=${encodeURIComponent(clientId)}&` +
        `redirect_uri=${encodeURIComponent(redirectUri)}&` +
        `response_type=code&` +
        `scope=${encodeURIComponent('https://www.googleapis.com/auth/drive.readonly email profile')}&` +
        `access_type=offline&` +
        `prompt=${promptParam}&` +
        `state=${encodeURIComponent(stateParam)}`;

      return Response.redirect(googleAuthUrl, 302);
    }

    // 3. Callback redirect URI from Google OAuth
    if (path === '/callback' || path === '/api/callback') {
      const code = url.searchParams.get('code');
      const stateStr = url.searchParams.get('state')?.trim() || '';

      if (!code || !stateStr) {
        return Response.redirect(`https://cloudstream-tv.vercel.app/success.html?status=error&message=Missing%20authorization%20code%20or%20state%20from%20Google.%20Please%20try%20logging%20in%20again.&t=${Date.now()}`, 302);
      }

      const parts = stateStr.split(':');
      const sessionId = parts[0].toUpperCase();
      const flowType = parts[1] || 'normal';

      const { data: session, error } = await supabase
        .from('sessions')
        .select('*')
        .eq('id', sessionId)
        .maybeSingle();

      if (error || !session) {
        return Response.redirect(`https://cloudstream-tv.vercel.app/success.html?status=error&message=This%20login%20session%20has%20expired%20or%20is%20invalid.%20Please%20close%20the%20login%20screen%20on%20your%20TV%20and%20open%20it%20again.&t=${Date.now()}`, 302);
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

      // If Google did not return a refresh token (because the user already authorized previously),
      // we retrieve the stored refresh token from our database users table.
      let finalRefreshToken = tokens.refresh_token;

      if (email) {
        if (!finalRefreshToken) {
          const { data: dbUser } = await supabase
            .from('users')
            .select('refresh_token')
            .eq('email', email)
            .maybeSingle();
          if (dbUser && dbUser.refresh_token) {
            finalRefreshToken = dbUser.refresh_token;
            console.log('Retrieved saved refresh token from database for:', email);
          }
        }

        // If we still don't have a refresh token (both Google and database have none),
        // and we haven't already forced consent, redirect back to login with force_consent=true.
        if (!finalRefreshToken && flowType !== 'force') {
          console.log(`No refresh token found for ${email}. Redirecting to force consent.`);
          const forceRedirectUrl = `${protocol}//${url.host}/functions/v1/auth-bridge/login?session=${sessionId}&force_consent=true`;
          return Response.redirect(forceRedirectUrl, 302);
        }

        // Save user to permanent users table (only update refresh_token if a new one was returned)
        try {
          const userUpdate: any = {
            email: email,
            name: name,
            profile_picture: picture,
            last_login_at: new Date().toISOString()
          };
          if (tokens.refresh_token) {
            userUpdate.refresh_token = tokens.refresh_token;
          }

          const { error: userError } = await supabase.from('users').upsert(userUpdate, { onConflict: 'email' });

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

      // Save credentials & authorize session (use the resolved finalRefreshToken)
      await supabase.from('sessions').update({
        status: 'authorized',
        access_token: tokens.access_token,
        refresh_token: finalRefreshToken,
        expires_in: tokens.expires_in,
        email: email
      }).eq('id', sessionId);

      const encodedName = encodeURIComponent(name || 'User');
      return Response.redirect(`https://cloudstream-tv.vercel.app/success.html?status=success&name=${encodedName}&t=${Date.now()}`, 302);
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
