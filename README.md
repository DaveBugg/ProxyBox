# ProxyBox

Open-source Android VPN client powered by [Xray-core](https://github.com/XTLS/Xray-core). Designed for both **mobile phones** and **Android TV / set-top boxes**.

## Features

- **Multi-protocol support**: VLESS, VMess, Shadowsocks, Trojan, Hysteria2
- **Transport layers**: TCP, WebSocket, gRPC, HTTP/2, QUIC, KCP, HTTPUpgrade, SplitHTTP
- **Security**: TLS, Reality, None
- **Android TV optimized**: dedicated TV interface with D-pad navigation and Leanback launcher support
- **Config import methods**:
  - Paste URL or raw JSON manually
  - Scan QR code with camera
  - Pick QR code image from gallery
  - Local HTTP server with QR code — scan from phone to add configs to TV/box
  - Subscription URLs with auto-refresh
- **Connection testing**: TCP ping for all profiles + HTTP connectivity test (google.com/generate_204)
- **Auto-update geo databases**: geoip.dat and geosite.dat from [v2fly](https://github.com/v2fly/geoip) via WorkManager (daily, Wi-Fi only)
- **Boot auto-start**: automatically reconnect VPN after device reboot
- **Local storage**: all configs stored locally in Room database

## Architecture

```
com.dave_cli.proxybox
├── core/
│   ├── CoreService.kt          # VPN service (TUN interface + xray engine)
│   ├── XrayManager.kt          # Xray core lifecycle (CoreController API)
│   ├── ConfigBuilder.kt        # Builds xray JSON config from profile
│   ├── GeoFileManager.kt       # Downloads geoip/geosite with ETag caching
│   ├── GeoUpdateWorker.kt      # WorkManager periodic updater
│   ├── ProxyEngine.kt          # Engine interface
│   └── BootReceiver.kt         # Auto-start on boot
├── data/
│   ├── db/                     # Room database (ProfileEntity, SubscriptionEntity)
│   └── repository/             # ProfileRepository (CRUD, ping, subscriptions)
├── import_config/
│   ├── ConfigParser.kt         # Parses vless://, vmess://, ss://, trojan://, hy2://, JSON
│   ├── SubscriptionParser.kt   # Decodes base64 subscription content
│   └── QrDecoder.kt            # Decodes QR codes from bitmap images
├── server/
│   ├── LocalConfigServer.kt    # NanoHTTPD server for phone-to-TV config transfer
│   └── QrGenerator.kt          # Generates QR code bitmaps
├── ui/
│   ├── main/                   # Mobile UI (MainActivity, ProfileAdapter)
│   ├── tv/                     # Android TV UI (TvMainActivity, TvProfileAdapter)
│   ├── add/                    # Add profile screen
│   └── server/                 # Local server screen with QR display
└── ProxyBoxApp.kt              # Application class (schedules geo updates)
```

## Requirements

- Android 7.0+ (API 24)
- Xray core via [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) (`libv2ray.aar`)

## Building

```bash
# Clone the repository
git clone https://github.com/youruser/ProxyBox.git
cd ProxyBox

# Build debug APK
./gradlew assembleDebug

# APK location
# app/build/outputs/apk/debug/app-debug.apk
```

> **Note**: The project includes `libv2ray.aar` in `app/libs/`. This contains the Xray core compiled for armeabi-v7a, arm64-v8a, x86, and x86_64.

## Adding Configs

### On Phone
1. Open app → tap **+ Add**
2. Paste a config URL (e.g. `vless://...`) or full JSON
3. Or scan a QR code with camera / pick QR image from gallery

### On Android TV / Box
1. Open app → select **Add Config via Phone**
2. Scan the displayed QR code with your phone
3. Browser opens → paste config URL, JSON, upload QR image, or add subscription

### Subscriptions
Add a subscription URL in the Add Config screen or via the local server web page. Profiles are fetched and stored automatically.

## Tech Stack

- **Language**: Kotlin
- **VPN Core**: Xray (via libv2ray.aar, CoreController API)
- **Database**: Room
- **Async**: Kotlin Coroutines + StateFlow
- **Networking**: OkHttp, NanoHTTPD
- **QR**: ZXing
- **Background tasks**: WorkManager
- **TV**: AndroidX Leanback

## License

This project is open source. See [LICENSE](LICENSE) for details.

## Credits

- [Xray-core](https://github.com/XTLS/Xray-core) — proxy engine
- [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) — Android bindings
- [v2rayNG](https://github.com/2dust/v2rayNG) — reference implementation
- [v2fly/geoip](https://github.com/v2fly/geoip) — geo databases
