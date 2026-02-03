# NanoMaps

AI-powered street view generation for Android. Transform any location on the map into a stunning, AI-generated street-level image.

## Overview

NanoMaps is an Android application that combines interactive mapping with Google's Gemini AI to generate realistic street view images. Simply tap anywhere on the map, set your viewing direction, and let AI create a unique visualization of that location.

## Features

- **Interactive Map** - OpenStreetMap-based map with street and satellite layer options
- **Location Search** - Search for any place worldwide using the built-in search bar
- **Direction Control** - Drag from your marker to set the viewing direction (0-360 degrees)
- **AI Generation** - Powered by Google's Gemini API for high-quality image generation
- **Multiple Styles** - Choose from 5 artistic styles:
  - Photorealistic (Google Street View quality)
  - Cinematic Golden Hour (dramatic lighting)
  - Moody Rainy Day (atmospheric reflections)
  - Retro Throwback (vintage film aesthetic)
  - Anime World (Japanese animation style)
- **Aspect Ratios** - Support for 16:9, 4:3, 3:4, 1:1, 9:16, and 21:9
- **Multiple Resolutions** - Generate images in 1K, 2K, or 4K quality
- **Custom Prompts** - Add your own instructions to guide the AI generation
- **Save to Gallery** - Export generated images directly to your device
- **Material 3 Design** - Modern, clean UI with dynamic colors and dark mode support
- **Edge-to-Edge** - Immersive full-screen experience

## Screenshots

*Screenshots coming soon*

## Requirements

- Android 7.0 (API 24) or higher
- Google Gemini API key (free tier available)

## Getting Started

### 1. Get a Gemini API Key

1. Visit [Google AI Studio](https://aistudio.google.com/apikey)
2. Sign in with your Google account
3. Create a new API key
4. Copy the key for use in the app

### 2. Install the App

Download the latest APK from the [Releases](https://github.com/yourusername/NanoMaps/releases) page.

Or build from source:

```bash
git clone https://github.com/yourusername/NanoMaps.git
cd NanoMaps
./gradlew assembleDebug
```

### 3. Configure the App

1. Open NanoMaps
2. Go to Settings (bottom navigation)
3. Enter your Gemini API key
4. Choose your preferred style, aspect ratio, and resolution
5. Return to the Map screen to start generating

## Usage

1. **Select Location**: Tap anywhere on the map to place a marker
2. **Set Direction**: Drag from the marker to set the viewing direction
3. **Customize** (optional): Enter a custom prompt for specific details
4. **Generate**: Tap the "Generate View" button
5. **View & Save**: View the result fullscreen and save to your gallery

## Tech Stack

- **Language**: Kotlin
- **UI**: Material 3 (Material Design 3)
- **Architecture**: MVVM with ViewModel and LiveData
- **Maps**: OSMDroid (OpenStreetMap)
- **Networking**: Kotlin Coroutines with OkHttp
- **AI**: Google Gemini API (imagen model)
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)

## Project Structure

```
app/src/main/
├── java/com/example/nanomaps/
│   ├── MainActivity.kt           # Main activity with navigation
│   ├── data/
│   │   ├── GeminiApiService.kt   # Gemini API integration
│   │   └── PreferencesRepository.kt # Settings persistence
│   └── ui/
│       ├── map/
│       │   ├── MapFragment.kt    # Map and generation UI
│       │   └── MapViewModel.kt   # Map state and generation logic
│       └── settings/
│           ├── SettingsFragment.kt
│           └── SettingsViewModel.kt
└── res/
    ├── layout/                   # XML layouts
    ├── values/                   # Strings, colors, themes, dimens
    ├── values-night/             # Dark mode colors
    ├── drawable/                 # Vector drawables
    ├── drawable-night/           # Dark mode drawables
    ├── anim/                     # Animations
    └── mipmap-*/                 # App icons
```

## Building

### Debug Build

```bash
./gradlew assembleDebug
```

The APK will be in `app/build/outputs/apk/debug/`.

### Release Build

1. Create a keystore if you don't have one
2. Configure signing in `app/build.gradle.kts`
3. Run:

```bash
./gradlew assembleRelease
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [OpenStreetMap](https://www.openstreetmap.org/) for map data
- [OSMDroid](https://github.com/osmdroid/osmdroid) for Android map rendering
- [Google Gemini](https://ai.google.dev/) for AI image generation
- [Material Design 3](https://m3.material.io/) for design guidelines

## Disclaimer

Generated images are AI-created artistic interpretations and may not accurately represent real-world locations. Use for creative and entertainment purposes only.
