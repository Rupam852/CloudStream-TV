# CloudStream TV - Cloudflare Edge Video Proxy & CDN Worker

This worker acts as an **Edge Stream Relay & CDN Proxy** for CloudStream TV. It accelerates Google Drive video playback across international and local ISPs using Cloudflare's Edge Network (HTTP/3 & Anycast Routing).

---

## 🚀 How to Deploy to Cloudflare (Free)

### Method 1: Using the Cloudflare Web Dashboard (Quickest - No CLI Needed)

1. Go to [dash.cloudflare.com](https://dash.cloudflare.com/) and log in (create a free account if you haven't already).
2. On the left sidebar, click **Compute (Workers & Pages)** > **Workers & Pages**.
3. Click **Create Application** > **Create Worker**.
4. Give it a name (e.g. `cloudstream-edge-proxy`) and click **Deploy**.
5. Click **Edit Code**, delete existing code, and paste the code from [`worker.js`](worker.js).
6. Click **Deploy / Save**.
7. Copy your Worker URL (e.g. `https://cloudstream-edge-proxy.<your-subdomain>.workers.dev`).

---

### Method 2: Deploying via Terminal (Wrangler CLI)

```bash
cd "cloudflare-worker"
npm install
npx wrangler login
npx wrangler deploy
```

---

## ⚡ How It Works

- **Stream Endpoint:** `https://your-worker-subdomain.workers.dev/stream?fileId=GOOGLE_DRIVE_FILE_ID`
- **Range Seeking:** Supports `Range: bytes=0-` for instant seeking inside movies.
- **Auth Forwarding:** Accepts `Authorization: Bearer <token>` header from CloudStream TV app.
- **Acceleration:** Cloudflare's closest Edge PoP connects to Google Drive via backbone fiber and streams chunked video to the TV with HTTP/3.
