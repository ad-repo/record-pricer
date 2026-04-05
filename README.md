# Record Pricer

Android app that identifies vinyl records and fetches current market prices from Discogs and eBay sold listings.

## How it works

1. Open the app and point the camera at an album cover — tap the screen to capture
2. Google Vision identifies the album from the cover art
3. Discogs returns matching vinyl releases ranked by popularity
4. Tap any result to load Discogs marketplace prices and recent eBay USA sold prices

Barcodes on the spine or back cover are scanned automatically — no photo needed.
Manual artist/album entry is available as a fallback.

## Setup

On first launch the app opens a settings screen. Enter your API keys:

| Key | Where to get it |
|-----|----------------|
| Google Vision API Key | [Google Cloud Console](https://console.cloud.google.com) → APIs & Services → Cloud Vision API |
| Discogs Personal Access Token | [Discogs Developer Settings](https://www.discogs.com/settings/developers) |
| eBay App ID *(optional)* | [eBay Developer Program](https://developer.ebay.com) → Application Keys |

Keys are stored encrypted on-device and survive reinstalls. They are never logged or transmitted anywhere other than the respective APIs.

## Tech stack

- Kotlin, minSdk 26 (Android 8)
- CameraX + ML Kit barcode scanning
- Google Cloud Vision API (WEB_DETECTION)
- Discogs API
- eBay Finding API
- Retrofit2 + OkHttp + Gson
- EncryptedSharedPreferences (AES256-GCM)
