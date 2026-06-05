# CloudStream TV

CloudStream TV is a premium, native Android TV application built using **Kotlin** and **Jetpack Compose for TV**. It is designed specifically for television screens to stream videos and display photo slideshows directly from **Google Drive** folders using either a public scraper or secure Google OAuth 2.0 Device flow.

> [!IMPORTANT]
> **Target Platform**: This application is built and optimized exclusively for **Android TV** and **Google TV** devices. It requires a D-pad remote control, keyboard, or equivalent controller to navigate the user interface.

---

## 📺 Key Features

- **ExoPlayer Video Streaming**: Premium media playback supporting Play/Pause, fast-forward/rewind, aspect ratio scaling (fit/fill/zoom), and playback speed controls.
- **Photo Slideshows**: Beautiful, automated image slideshows with custom transition intervals (3s, 5s, 10s, 15s) and manual traversal.
- **Google OAuth 2.0 (Device Flow)**: Secure sign-in designed for TV devices. Displays an activation link alongside a dynamic **QR Code** for quick scanning from a phone or computer.
- **Lag-Free TV Performance**: Native D-pad focus animations, unique keys for scroll item caching in all lazy grids/lists, hardware-accelerated `.graphicsLayer` alpha rendering, and isolated playback seekbar recompositions to guarantee smooth 60FPS TV performance on budget TV processors.
- **Mutex Token Sync & Secure Caching**: Parallel network requests are synchronized with a coroutine `Mutex` to prevent redundant server token refreshes. All credentials are encrypted and stored locally.
- **Recents & History**: Seamless shelf showing recently streamed items, with one-press retry and clear history actions.
- **Overlay Sidebar**: Expandable navigation menu for switching between folders, adding new drives, and toggling dark/light themes.

---

## 🚫 Supported Formats

CloudStream TV is strictly optimized for **Video** and **Photo** streaming. Other file formats are not supported.

### ✅ Supported Media Type Streams
- **Videos**: `.mp4`, `.mkv`, `.webm`, `.m4v`, `.avi`, `.mov`, `.wmv`, `.3gp`, `.ts`, `.m2ts`, `.flv`, `.asf`, `.vob`, `.ogv`
- **Photos**: `.jpg`, `.jpeg`, `.png`, `.webp`, `.bmp`, `.gif`, `.heic`, `.heif`, `.tiff`, `.svg`, `.ico`

### ❌ Unsupported Formats (Displays Warning Toast)
- Documents (`.pdf`, `.docx`, `.xlsx`, `.pptx`)
- Raw text files (`.txt`, `.json`)
- Audio-only files (`.mp3`, `.wav`, `.flac`, `.m4a`)
- Unsupported formats will trigger a `"File streaming not supported"` message.

---

## 🔗 Google Drive Folder Link & Access Requirements

To browse and stream folders, you must supply a folder URL or folder ID. The app handles two modes of folder access:

### 1. Public Folders (Scraper Mode)
- **Requirement**: The Google Drive folder **must be shared publicly**.
- **Sharing Settings**: Right-click the folder in Google Drive -> click **Share** -> under **General Access**, change from *Restricted* to **"Anyone with the link"** and set the role to **"Viewer"**.
- If this permission is not set, the public scraper will fail to fetch media items.

### 2. Private Folders (Google Authenticated Mode)
- **Requirement**: If the folder is private, you must link it and log in.
- **Sharing Settings**: The Google Account that you use to log in via the QR code/Device activation flow **must have permission** to view the linked Google Drive folder.

### Accepted Link Formats
You can input either the full sharing link or the folder ID directly:
*   **Full URL**: `https://drive.google.com/drive/folders/1a2b3c4d5e6f7g8h9i0j_k_l_m_n_o_p`
*   **Folder ID**: `1a2b3c4d5e6f7g8h9i0j_k_l_m_n_o_p`

---

## 🌐 OAuth Web Login Backend Bridge

The project includes a lightweight Node.js/Express authentication server located in the [backend](file:///d:/PROJECT/CloudStream%20TV/backend) directory. It serves as an authorization bridge to handle Google OAuth 2.0 credentials safely for TV devices.

### How it Works
1. **Initiate Session**: When the TV user clicks **Link Folder & Google Authenticate**, the TV app calls the backend to register a temporary session ID.
2. **Scan & Authorize**: The TV app displays a **QR Code** pointing to the backend login URL. The user scans it with a phone or computer browser, redirects to Google's standard web authentication page, and grants read-only Google Drive access.
3. **Token Callback**: Google redirects back to the backend callback endpoint with an auth code. The backend exchanges this code for access/refresh tokens and securely caches them.
4. **Polling & Completion**: The TV app polls the backend every few seconds using the session ID. Once tokens are retrieved, they are securely saved locally inside the TV's encrypted preferences, and the backend session cache is cleared.

### Environment Configuration
To deploy the backend (e.g., to Render or Heroku), configure the following environment variables:
- `GOOGLE_CLIENT_ID`: Google Developer Console OAuth Web Client ID.
- `GOOGLE_CLIENT_SECRET`: Google Developer Console OAuth Web Client Secret.
- `GOOGLE_REDIRECT_URI`: The authorized redirect callback URL (e.g., `https://your-app.onrender.com/api/callback`).

---

## 🛠️ Technology Stack

- **Core**: Kotlin & Android SDK
- **UI Framework**: Jetpack Compose for TV (Material 3)
- **Video Playback**: AndroidX Media3 ExoPlayer
- **Image Loading**: Coil (with HTTP cache headers and crossfades)
- **Networking**: OkHttp3 & Google Drive REST API
- **JSON Parser**: Gson

---

## 🚀 Installation & Build

Pre-compiled APKs are available in the root folder of the project:
*   **Release APK**: `app-release.apk` (Optimized, signed production bundle)
*   **Debug APK**: `app-debug.apk` (Development build)

### Building from Source
Ensure you have the Android SDK path configured in `local.properties`. Run the following command in the project root:

```bash
# Compile both configurations
./gradlew.bat assembleRelease assembleDebug
```
