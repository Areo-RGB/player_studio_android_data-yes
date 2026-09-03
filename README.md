# Playlist Player (Android)

A native Android video exercise and chapter playlist application built with **Kotlin** and **Jetpack Compose**.

## Features

- **Multi-Source Video Engine**: Supports high-performance direct video streaming via AndroidX Media3 (ExoPlayer) as well as embedded YouTube drills.
- **Precision Chapter Navigation**: Interactive chapter/drill drawer with video segment timestamps, duration formatting, and active drill indicators.
- **Smart Looping & Speed Controls**: Seamless single-chapter looping and **0.25x Slow Motion** playback for careful footwork analysis and training drills.
- **Built-in Interval & Countdown Timer**: Configurable workout timer with minutes/seconds input, start/pause/resume/stop controls, and countdown alerts.
- **Playlists Drawer**: Fast switcher between training programs (FIFA+ Collection, 10 Fast Feet Exercises, Ball Mastery, Juggling Skills).
- **Responsive Landscape UI**: Optimized dark athletic UI with smooth slide-over sidebars, quick prev/next navigation overlays, and Material Design 3 styling.

## Tech Stack

- **UI Framework**: Jetpack Compose & Material 3
- **Language**: Kotlin 2.0
- **Media Playback**: AndroidX Media3 (ExoPlayer 1.5.1) & Android WebView YouTube Player
- **Image Loading**: Coil Compose 2.7.0
- **Architecture**: Clean MVVM with Kotlin Coroutines and StateFlow
