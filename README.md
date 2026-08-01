# CloudStream Desktop (Unofficial Client)

[![Platform: Linux & Windows](https://img.shields.io/badge/Platform-Linux%20%7C%20Windows-blue?style=for-the-badge&logo=linux)](https://github.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Compose Desktop](https://img.shields.io/badge/Compose%20Multiplatform-1.7+-4285F4?style=for-the-badge&logo=jetpackcompose)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![JDK Compatibility](https://img.shields.io/badge/JDK-21%20--%2026-007396?style=for-the-badge&logo=openjdk)](https://openjdk.org)

**CloudStream Desktop** is a native Compose Multiplatform desktop JVM port of [CloudStream 3](https://github.com/recloudstream/cloudstream), designed to run `.cs3` Dalvik plugins natively on Linux and Windows without requiring an Android emulator.

---

## 🚀 Key Features & Linux Port Enhancements

- **Cross-Platform Support:** Fully ported and optimized for Linux (tested on Arch Linux under Wayland & Hyprland) and Windows.
- **Embedded Hardware Video Playback:** Uses `libmpv` (System `libmpv.so.2` / `libmpv.so.1` on Linux, or bundled DLL on Windows) embedded via AWT `Canvas` and JNA.
- **Linux Wayland / Hyprland Embedding:** Uses `gpu-context=x11egl` for MPV window embedding (`--wid`) under XWayland, preventing MPV from spawning external windows.
- **C Locale Process Initializer:** Forces `LC_NUMERIC="C"` via JNA `setlocale` to prevent native `libmpv` initialization failures on non-C system locales.
- **JDK 21–26+ Compatibility:** Safe execution on JDK 24+ (e.g. OpenJDK 26) with conditional SecurityManager handling following JEP 486.
- **Playwright Cloudflare Bypass:** Integrated Playwright driver with platform-aware driver stripping (`stripPlaywrightDriver` task) to keep distribution packages optimized (reducing bundle size from 206MB down to 125MB).
- **Linux Packaging:** Built-in Gradle distribution tasks for `.deb` and `.AppImage` packages.

---

## 🛠 Architecture Overview

```text
cloudstream-desktop-unofficial/
├── android-reference/           # Submodule pointing to official CloudStream Android core
├── android-stubs/               # Mocked Android APIs (Context, Log, Uri, Intent) for plain JVM
├── common/                      # Shared data models, storage utilities, and logging interfaces
├── library/                     # Wrapper module exposing upstream scrapers to desktop JVM
├── player-abstraction/          # Pure Kotlin Video Player IPC & JNA MPV/VLC bridges
├── plugin-runtime/              # Runtime dex2jar converter & PluginSecurityVerifier (ASM analyzer)
├── plugin-sandbox/              # Testing environment for validating Dalvik bytecode
└── desktop-app/                 # Main Compose Multiplatform Desktop Application
    ├── src/main/kotlin/com/lagradost/cloudstream3/desktop/
    │   ├── Main.kt              # App entry point (Interop blending & Bootstrap initializer)
    │   ├── init/                # Startup logic (Network, Security, Proxy, Plugins, Auto-Updater)
    │   ├── logic/               # MVVM ViewModels handling business logic
    │   ├── network/             # DNS-over-HTTPS (DoH) & NiceHttp network clients
    │   ├── player/              # ComposeMpvPlayer & MpvLibrary (JNA bindings)
    │   ├── repo/                # Third-party plugin repository manager
    │   ├── storage/             # DesktopDataStore JSON configuration manager
    │   └── ui/                  # Jetpack Compose for Desktop screens & Netflix player UI
    └── build.gradle.kts         # Gradle build script with Linux DEB/AppImage packaging
```

---

## 📦 Building & Running

### Prerequisites
- **JDK 21 or higher** (Targeting JDK 21+ / JDK 26).
- **Git** (Required for cloning submodules).
- **MPV:** On Linux, install `mpv` via your system package manager (e.g., `sudo pacman -S mpv` on Arch Linux, or `sudo apt install libmpv-dev` on Ubuntu/Debian).

### 1. Clone the Repository
Always clone with `--recursive` to fetch the `android-reference` submodule:
```bash
git clone --recursive https://github.com/SusyBegula/cloudstream-desktop-unofficial.git
cd cloudstream-desktop-unofficial
```

> [!WARNING]  
> **DO NOT download as a ZIP file** from GitHub, as ZIP downloads exclude submodules and will cause compilation failures in `:library`.

### 2. Run Locally in Development Mode
```bash
./gradlew desktop-app:run
```

### 3. Run Unit Tests
```bash
./gradlew desktop-app:test
```

### 4. Package Native Linux / Windows Distributions

#### **Linux (.deb & .AppImage packages):**
```bash
./gradlew desktop-app:packageDeb
./gradlew desktop-app:packageAppImage
```
Output binaries will be generated in `desktop-app/build/compose/binaries/main/deb/` and `desktop-app/build/compose/binaries/main/appimage/`.

#### **Windows (.msi installer):**
```bash
./gradlew desktop-app:packageMsi
```
Output installer will be generated in `desktop-app/build/compose/binaries/main/msi/`.

---

## 📜 DMCA Notice & Disclaimer

**This repository acts purely as a blank-slate media player shell.**  
The application does not ship with, host, or distribute any plugins, media files, or pre-configured content sources. Everything must be explicitly installed by the user at their own discretion. The developers hold no responsibility or liability for how users utilize this software.

---

## 🙏 Acknowledgements

Acknowledgement to the original **CloudStream** project developers and contributors for their open-source Android codebase and extension architecture.
