# ShortsBlocker

ShortsBlocker is a modern, lightweight, and privacy-focused Android utility built with Kotlin and Jetpack Compose that automatically detects and blocks distracting short-form video feeds (YouTube Shorts, Facebook Reels, and Instagram Reels) using the Android Accessibility Service.

## Features

- **Automated Feed Interception**: Automatically performs a back action when short-form video player nodes are detected in YouTube, Facebook, and Instagram.
- **Granular Platform Controls**: Individual toggle switches for YouTube Shorts, Facebook Reels, and Instagram Reels.
- **Focus & Time Saved Analytics**: Real-time counter of blocked reels/shorts with estimated time saved and platform breakdown.
- **Live Permission Status**: Visual status card indicating whether the Accessibility Service is enabled in system settings, with one-tap navigation to Android settings.
- **Modern Jetpack Compose UI**: Built with Material Design 3, dynamic theme support, edge-to-edge rendering, and adaptive layouts.
- **100% Offline & Private**: Zero network permissions, no telemetry, no tracking, and no external servers.

## Architecture

- **UI Layer**: Jetpack Compose with Material 3, ViewModel, and StateFlow.
- **Accessibility Service**: `ShortsBlockerService` listens for window and content change events from target apps and closes reel screens.
- **Persistence**: SharedPreferences for local configuration and block stats.

## How to Get the APK

### 1. Directly from AI Studio
- Click on the project settings / export menu in the top right corner.
- Select **Export project as ZIP** or **Generate APK / AAB**.

### 2. Automatically via GitHub Actions (CI/CD)
1. Push this repository to GitHub.
2. Go to the **Actions** tab on your GitHub repository.
3. The **Build & Release Android APK** workflow will automatically run.
4. Once completed, click on the workflow run and download the **ShortsBlocker-Debug-APK** artifact from the Artifacts section.

### 3. Build Locally with Gradle
```bash
gradle assembleDebug
# The APK will be generated at:
# app/build/outputs/apk/debug/app-debug.apk
```

