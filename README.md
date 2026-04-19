# Musify

Metrolist is a modern, feature-rich music player for Android, built with Jetpack Compose. It allows you to stream and download music from YouTube Music, manage your local library, and enjoy a seamless listening experience with Material 3 aesthetics.

Metrolist is a fork of InnerTune, continuing the mission of providing a clean, ad-free music experience.

## ✨ Features

- **YouTube Music Streaming**: Access the vast library of YouTube Music.
- **Offline Playback**: Download your favorite songs and playlists for offline listening.
- **Local Library**: Manage and play your local music files.
- **Synchronized Lyrics**: Support for synchronized lyrics via LRCLIB and BetterLyrics.
- **Personalized Stats**: Track your listening habits with detailed history and statistics.
- **Material 3 UI**: Beautiful, modern interface with support for dynamic colors and customizable themes.
- **Last.fm Integration**: Scrobble your tracks to Last.fm.
- **Android Auto Support**: Listen to your music safely while driving.
- **Multiple Flavors**:
  - **FOSS**: Fully open-source, F-Droid compatible, no Google Play Services.
  - **GMS**: Includes Google Cast support (requires Google Play Services).
  - **Izzy**: Tailored for the IzzyOnDroid repository.

## 🛠️ Technical Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose)
- **Media Engine**: [Media3 (ExoPlayer)](https://developer.android.com/guide/topics/media/media3)
- **Database**: [Room](https://developer.android.com/training/data-storage/room)
- **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- **Networking**: [Ktor](https://ktor.io/) & [OkHttp](https://square.github.io/okhttp/)
- **Image Loading**: [Coil](https://coil-kt.github.io/coil/)
- **Serialization**: [Kotlin Serialization](https://kotlinlang.org/docs/serialization.html) & [Protobuf](https://protobuf.dev/)

## 🚀 Building from Source

### Prerequisites

- **JDK 21**: Ensure you have Java Development Kit 21 installed.
- **Android SDK**: Latest platform tools and SDK.
- **Protobuf Compiler**: `protoc` v3.21 or newer.
- **Git Submodules**: This project uses submodules.

### Setup

1. **Clone the repository**:
   ```bash
   git clone github.com/Telebotfaroff/Metrolist/
   cd Metrolist
   ```

2. **Initialize submodules**:
   ```bash
   git submodule update --init --recursive
   ```

3. **Generate Protobuf files**:
   ```bash
   cd app
   bash generate_proto.sh
   cd ..
   ```

4. **API Keys (Optional)**:
   To enable Last.fm support, add your API keys to `local.properties` or set them as environment variables:
   ```properties
   LASTFM_API_KEY=your_api_key
   LASTFM_SECRET=your_secret
   ```

### Building for Development

To build a debug APK (FOSS variant):
```bash
./gradlew :app:assembleFossDebug
```
The APK will be located at `app/build/outputs/apk/foss/debug/app-foss-debug.apk`.

### Building for Publishing

To build a release APK for publishing, you need to configure a signing keystore.

1. **Environment Variables**:
   Set the following environment variables for signing:
   - `STORE_PASSWORD`: Password for your keystore.
   - `KEY_ALIAS`: Alias for your signing key.
   - `KEY_PASSWORD`: Password for your signing key.

2. **Keystore Placement**:
   Place your release keystore at `app/keystore/release.keystore`.

3. **Build Command**:
   Choose the flavor you want to build (e.g., FOSS):
   ```bash
   ./gradlew :app:assembleFossRelease
   ```
   The signed release APK will be located at `app/build/outputs/apk/foss/release/app-foss-release.apk`.

For other variants, use `assembleGmsRelease` or `assembleIzzyRelease`.

## 🎨 Customization

### Changing the App Name
You can easily customize the application name for your own builds:

1. **Environment Variable (Recommended for CI/CD)**:
   Set the `METROLIST_APP_NAME` environment variable before building:
   ```bash
   export METROLIST_APP_NAME="MyMusicApp"
   ./gradlew :app:assembleFossRelease
   ```

2. **Manual Resource Change**:
   Modify the value in `app/src/main/res/values/app_name.xml`:
   ```xml
   <string name="app_name">MyMusicApp</string>
   ```

3. **Application ID Override**:
   If you are forking the project and want to install it alongside the original, you can also override the Application ID using the `METROLIST_APPLICATION_ID` environment variable.

## 🤝 Contributing

Contributions are welcome! Please refer to the [Development Guide](development_guide.md) for more information on how to set up your environment and follow our coding standards.

## 📄 License

This project is licensed under the [GPL-3.0 License](LICENSE).
