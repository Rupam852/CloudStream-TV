/**
 * CloudStream TV - Cloudflare Edge Video Stream Proxy & CDN Worker
 * 
 * Features:
 * 1. Edge-accelerated Google Drive video proxying with HTTP/3 & QUIC.
 * 2. Full HTTP 206 Partial Content (Range: bytes=...) header pass-through.
 * 3. Bypasses ISP throttling on direct Google Drive downloads.
 * 4. Automatic Bearer Token forwarding for authorized Google Drive streams.
 * 5. Global Anycast Edge Routing for minimum initial video buffering latency.
 */

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // Handle CORS Preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: getCorsHeaders(),
      });
    }

    // Health check endpoint
    if (url.pathname === "/" || url.pathname === "/health") {
      return new Response(
        JSON.stringify({
          status: "healthy",
          service: "CloudStream TV Edge CDN Worker",
          version: "1.0.0",
          colo: request.cf?.colo || "UNKNOWN",
          httpProtocol: request.cf?.httpProtocol || "HTTP/2",
        }),
        {
          status: 200,
          headers: {
            "Content-Type": "application/json",
            ...getCorsHeaders(),
          },
        }
      );
    }

    // Streaming endpoint: /stream?fileId=... or /stream/:fileId
    if (url.pathname.startsWith("/stream")) {
      const fileId = url.searchParams.get("fileId") || url.pathname.split("/")[2];
      if (!fileId) {
        return new Response(
          JSON.stringify({ error: "Missing required 'fileId' query parameter or path segment" }),
          { status: 400, headers: { "Content-Type": "application/json", ...getCorsHeaders() } }
        );
      }

      // Prepare request headers
      const reqHeaders = new Headers();
      
      // Pass client range header (crucial for ExoPlayer seeking & chunk streaming)
      const range = request.headers.get("Range");
      if (range) {
        reqHeaders.set("Range", range);
      }

      // Pass Authorization header if present in request or query
      const authHeader = request.headers.get("Authorization");
      const tokenParam = url.searchParams.get("token");
      const apiKey = url.searchParams.get("key");

      if (authHeader) {
        reqHeaders.set("Authorization", authHeader);
      } else if (tokenParam) {
        reqHeaders.set("Authorization", `Bearer ${tokenParam}`);
      }

      // Build target URL
      let targetUrl = "";
      if (authHeader || tokenParam || apiKey) {
        targetUrl = `https://www.googleapis.com/drive/v3/files/${encodeURIComponent(fileId)}?alt=media&supportsAllDrives=true`;
        if (apiKey) {
          targetUrl += `&key=${encodeURIComponent(apiKey)}`;
        }
      } else {
        // Public/shared Google Drive direct download URL with bypass for large file virus warning
        targetUrl = `https://drive.usercontent.google.com/download?id=${encodeURIComponent(fileId)}&export=download&confirm=t`;
      }

      // Set standard browser user agent to ensure optimal Google peering
      reqHeaders.set(
        "User-Agent",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
      );

      try {
        let driveResponse = await fetch(targetUrl, {
          method: "GET",
          headers: reqHeaders,
          redirect: "follow",
        });

        // If usercontent fallback is needed or returns HTML confirmation
        const contentType = driveResponse.headers.get("Content-Type") || "";
        if (contentType.includes("text/html") && !authHeader && !tokenParam) {
          const fallbackUrl = `https://drive.google.com/uc?export=download&id=${encodeURIComponent(fileId)}&confirm=t`;
          driveResponse = await fetch(fallbackUrl, {
            method: "GET",
            headers: reqHeaders,
            redirect: "follow",
          });
        }

        // Forward response with permissive CORS and optimized streaming headers
        const resHeaders = new Headers(driveResponse.headers);
        Object.entries(getCorsHeaders()).forEach(([k, v]) => resHeaders.set(k, v));
        
        // Ensure accept-ranges is advertised
        resHeaders.set("Accept-Ranges", "bytes");
        resHeaders.set("X-Edge-Served-By", request.cf?.colo || "Cloudflare-Edge");

        return new Response(driveResponse.body, {
          status: driveResponse.status,
          statusText: driveResponse.statusText,
          headers: resHeaders,
        });
      } catch (err) {
        return new Response(
          JSON.stringify({
            error: "Failed to stream video from Google Drive",
            message: err.message || String(err),
          }),
          { status: 502, headers: { "Content-Type": "application/json", ...getCorsHeaders() } }
        );
      }
    }

    return new Response("Not Found", { status: 404, headers: getCorsHeaders() });
  },
};

function getCorsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, HEAD, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Range, Content-Type, Accept, X-Token-Refreshed, User-Agent",
    "Access-Control-Expose-Headers": "Content-Length, Content-Range, Accept-Ranges, Content-Type, X-Edge-Served-By",
  };
}
