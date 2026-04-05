# Record Pricer Android App

## Build Files
- [x] Create `settings.gradle.kts`
- [x] Create root `build.gradle.kts`
- [x] Create `app/build.gradle.kts`

## Manifest & Config
- [x] Create `app/src/main/AndroidManifest.xml`
- [x] Create `app/src/main/res/xml/file_paths.xml` (FileProvider)

## Source — API Layer
- [x] Create `ApiKeys.kt` (placeholder constants)
- [x] Create `api/VisionApi.kt` (Retrofit interface + models)
- [x] Create `api/DiscogsApi.kt` (Retrofit interface + models)
- [x] Create `api/EbayApi.kt` (Retrofit interface + models)

## Source — UI
- [x] Create `MainActivity.kt`
- [x] Create `res/layout/activity_main.xml`
- [x] Create `res/values/strings.xml`

## In-App API Key Management
- [x] Delete `ApiKeys.kt`
- [x] Add `security-crypto` dependency
- [x] Create `KeysPrefs.kt` (EncryptedSharedPreferences)
- [x] Create `SettingsActivity.kt` + `activity_settings.xml`
- [x] Register `SettingsActivity` in `AndroidManifest.xml`
- [x] Add ⚙ settings button to `activity_main.xml`
- [x] Update `MainActivity.kt` to use `KeysPrefs` (no more `ApiKeys`)
- [x] First-launch redirect to SettingsActivity if keys missing
