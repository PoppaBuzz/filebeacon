<img src="https://jphat.net/filebeacon/FileBeacon-icon-nobg.png" height="100"><img src="https://jphat.net/filebeacon/FileBeacon-text.png" height="100">
# FileBeacon

**Your files. Always within reach.**

FileBeacon is an Android app that turns your phone into a wireless file server. Start the server, open the displayed URL on any device sharing the same WiFi network, and you have a full-featured file manager in your browser — no cables, no cloud, no accounts required.

---

## App Screenshots

<img src="https://jphat.net/filebeacon/app/permissions.png" width="12%"><img src="https://jphat.net/filebeacon/app/no.wifi.png" width="12%">
<img src="https://jphat.net/filebeacon/app/wifi.connected.png" width="12%"><img src="https://jphat.net/filebeacon/app/nearby.devices.png" width="12%"><img src="https://jphat.net/filebeacon/app/security.settings.png" width="12%"><img src="https://jphat.net/filebeacon/app/port.number.png" width="12%"><img src="https://jphat.net/filebeacon/app/themes.png" width="12%">

---

## Web Server Screenshots

<img src="https://jphat.net/filebeacon/webserver/normal.png" width="20%"><img src="https://jphat.net/filebeacon/webserver/select.png" width="20%"><img src="https://jphat.net/filebeacon/webserver/search.png" width="20%"><img src="https://jphat.net/filebeacon/webserver/upload.png" width="20%"><img src="https://jphat.net/filebeacon/webserver/themes.png" width="20%">

---

## Features

### Web File Manager
- Browse the full phone file system from any browser
- Upload multiple files with drag-and-drop — original filenames always preserved
- Download files individually or bulk-select for batch download
- Create folders, rename, move, copy, and delete files
- Multi-file selection with batch operations (move, copy, delete, archive)
- Inline search across file names and file contents

### File Viewing
- Image gallery with slideshow mode
- Audio and video media player with Google Cast support
- Inline PDF viewer with PDF-to-image conversion
- Full-text search with content matching and line previews

### Archive Support
- Create ZIP archives from any selection of files
- Extract ZIP, RAR, TAR, and GZ archives
- Browse archive contents before extracting

### Themes & Customization
- 5 color themes: Light, Dark, Blue, Green, Purple
- 3 icon packs: Emoji (default), Minimal, Colorful
- All preferences persist across sessions

### Android App
- One-tap server start and stop
- Real-time WiFi status indicators
- Displays connected network (SSID) and the server URL
- Configurable server port
- Nearby device discovery via mDNS
- Optional HTTP Basic Auth password protection
- Multiple Material3 app themes (Light, Dark, AMOLED, Blue, Green, Purple, Sunset)
- Portrait and landscape layouts

---

## How It Works

FileBeacon runs [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) as an embedded HTTP server inside a foreground service on your Android device. When started, the app shows a local URL such as `http://192.168.1.100:8080`. Any device on the same WiFi network can open that URL in a browser to access the full web UI.

The web UI is plain HTML, CSS, and JavaScript, served directly from the app's assets. No internet connection is needed — everything runs on your local network.

---

## Requirements

- Android 8.0 (API 26) or higher
- Both devices connected to the same WiFi network
- Any modern browser (Chrome, Firefox, Safari, Edge)

### Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Run the local web server |
| `ACCESS_WIFI_STATE` | Read IP address and connection info |
| `ACCESS_FINE_LOCATION` | Required by Android to read the WiFi SSID |
| `READ_MEDIA_*` / `READ_EXTERNAL_STORAGE` | Browse and serve files |
| `WRITE_EXTERNAL_STORAGE` | Save uploaded files (Android ≤ 12) |
| `MANAGE_EXTERNAL_STORAGE` | Full file system access (Android 11+) |
| `CHANGE_WIFI_MULTICAST_STATE` | mDNS device discovery |
| `FOREGROUND_SERVICE` | Keep the server running while the app is backgrounded |

---

## Building

1. Clone the repository
2. Open in Android Studio (Hedgehog or newer)
3. Sync Gradle
4. Build and run on a device or emulator

```bash
./gradlew assembleDebug
```

The Montserrat font is required. Download from [Google Fonts](https://fonts.google.com/specimen/Montserrat) and place the files in `app/src/main/res/font/`:
- `Montserrat-Regular.ttf` → `montserrat_regular.ttf`
- `Montserrat-Bold.ttf` → `montserrat_bold.ttf`

### Dependencies

| Library | Version | Purpose |
|---|---|---|
| NanoHTTPD | 2.3.1 | Embedded HTTP server |
| NanoHTTPD Apache FileUpload | 2.3.1 | Multipart file upload parsing |
| JmDNS | — | mDNS/Bonjour device discovery |
| Gson | 2.13.2 | JSON serialization |
| Apache Commons Compress | 1.28.0 | ZIP/TAR archive support |
| Junrar | 7.5.7 | RAR archive extraction |
| Kotlin Coroutines | 1.10.2 | Async file search |
| Material3 | — | UI components |

---

## Project Structure

```
app/src/main/
├── java/com/jphat/filebeacon/
│   ├── MainActivity.kt             # App entry point, UI, server control
│   ├── WebServer.kt                # NanoHTTPD server, all HTTP endpoints
│   ├── FileManager.kt              # File operations (save, delete, move, copy)
│   ├── FileExplorerService.kt      # Foreground service wrapper
│   ├── ArchiveManager.kt           # ZIP/RAR/TAR create and extract
│   ├── SearchManager.kt            # File name and content search
│   ├── AuthManager.kt              # HTTP Basic Auth
│   ├── DeviceDiscoveryManager.kt   # mDNS advertising and discovery
│   ├── ThemeManager.kt             # App theme management
│   ├── ThemeSelectionDialog.kt     # Theme picker dialog
│   ├── PdfConverter.kt             # PDF to image conversion
│   ├── ResumableTransferManager.kt # Resume offset tracking
│   └── TransferTaskManager.kt      # Upload/download task tracking
├── assets/
│   ├── script.js                   # Web UI JavaScript
│   ├── style.css                   # Web UI styles and themes
│   ├── manifest.json               # PWA manifest
│   └── sw.js                       # Service worker
└── res/
    ├── layout/activity_main.xml      # Portrait layout
    └── layout-land/activity_main.xml # Landscape layout
```

---

## Security

By default the server is accessible to anyone on the local network. To restrict access:

1. Tap **Security Settings** in the app
2. Set a password
3. Enable authentication

Once enabled, the browser will prompt for credentials. Access uses HTTP Basic Authentication.

> **Note:** HTTP Basic Auth is not encrypted. Use password protection only on trusted private networks. Never expose the server port to the public internet.

---

## License

This project is open source. See [LICENSE](LICENSE) for details.

---

## Author

Built by **jPHat**
