# Record Pricer

## Android app that identifies vinyl records and fetches Discogs sold prices and current eBay listings.
<p>
  <img src="https://github.com/user-attachments/assets/6e13bc8f-8f6f-4f73-9123-e023d5af1568" width="45%" />
  <img src="https://github.com/user-attachments/assets/6e1e56f2-0d9c-4dee-a63c-459351a57b6a" width="45%" />
</p>

## How it works

1. Open the app and point the camera at an album cover — tap the screen to capture
2. Google Vision identifies the album from the cover art
3. Discogs returns matching vinyl releases ranked by popularity
4. Tap any result to load the lowest current Discogs listing price plus a link to its full sales history, and current eBay USA listings

Barcodes on the spine or back cover are scanned automatically — no photo needed.
Manual artist/album entry is available as a fallback.

## Setup

On first launch the app opens a settings screen. Enter your API keys. Google Vision and Discogs are required; eBay is optional but enables current listing price lookups alongside the Discogs sold prices.

---

### Google Vision API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com) and create a project (or select an existing one).
2. Navigate to **APIs & Services → Library**, search for **Cloud Vision API**, and click **Enable**.
3. Go to **APIs & Services → Credentials**, click **Create Credentials → API key**.
4. Copy the key. Optionally restrict it to the Cloud Vision API under **Edit key → API restrictions**.

---

### Discogs Personal Access Token

1. Log in to [Discogs](https://www.discogs.com) and go to **Settings → Developers** (or visit [discogs.com/settings/developers](https://www.discogs.com/settings/developers)).
2. Click **Generate new token**.
3. Copy the token shown — it won't be displayed again.

---

### eBay App ID & Client Secret *(optional)*

The app uses the eBay OAuth2 client credentials flow, so you need both the App ID and Client Secret.

1. Sign up or log in at [developer.ebay.com](https://developer.ebay.com).
2. Go to **My Account → Application Keys** and create an application if you don't have one.
3. Under the **Production** keyset, copy the **App ID (Client ID)** and **Client Secret**.
4. Enter both values in the app settings.

---

Keys are stored encrypted on-device using AES256-GCM and survive reinstalls. They are never logged or transmitted anywhere other than the respective APIs.

## Tech stack

- Kotlin, minSdk 26 (Android 8)
- CameraX + ML Kit barcode scanning
- Google Cloud Vision API (WEB_DETECTION)
- Discogs API
- eBay Browse API (Buy API)
- Retrofit2 + OkHttp + Gson
- EncryptedSharedPreferences (AES256-GCM)
