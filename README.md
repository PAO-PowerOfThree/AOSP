# AOSP
AOSP platform customization including framework modifications, system service extensions, and HAL integrations.


# AOSP Automotive Test UI Application

## PAO - Passenger Display

Below are screenshots from ***trout*** of each activity/fragment.

### MainActivity
![MainActivity Screenshot](photos/main1.png)
![MainActivity Screenshot](photos/main2.png)


### AmbientLightActivity
![AmbientLightActivity Screenshot](photos/light1.png)
![AmbientLightActivity Screenshot](photos/light2.png)
![AmbientLightActivity Screenshot](photos/light3.png)
![AmbientLightActivity Screenshot](photos/light4.png)


### HvacActivity
![HvacActivity Screenshot](photos/hvac1.png)
![HvacActivity Screenshot](photos/hvac2.png)
![HvacActivity Screenshot](photos/hvac3.png)


### NavigationActivity
![NavigationActivity Screenshot](photos/map1.png)
![NavigationActivity Screenshot](photos/map2.png)
![NavigationActivity Screenshot](photos/map3.png)


### VoskDialogFragment (Voice Recognition)
![FingerprintActivity Screenshot](photos/voice.png)


This project is an Android Open Source Project (AOSP) based application designed for automotive environments, specifically targeting Android Automotive OS (AAOS). It provides a comprehensive test UI for various vehicle-related features, including ambient lighting control, fingerprint authentication, HVAC (Heating, Ventilation, and Air Conditioning) management, music playback, navigation with GPS integration, over-the-air (OTA) updates, and voice recognition using the Vosk speech-to-text engine. The app leverages Android's Car API and Vehicle HAL (VHAL) to interact with vehicle hardware properties.

The application is built in Kotlin and demonstrates integration with automotive-specific APIs, such as `CarPropertyManager` for reading/writing vehicle properties (e.g., fan speed, LED strips, GPS location). It includes immersive UI elements, dark/light mode support, animations, and voice-activated controls for a seamless in-car experience.

## Table of Contents

- [Features](#features)
- [Architecture and File Explanations](#architecture-and-file-explanations)
- [Screenshots](#screenshots)
- [Installation and Setup](#installation-and-setup)
- [Usage](#usage)
- [Dependencies](#dependencies)
- [Known Issues](#known-issues)
- [Contributing](#contributing)
- [License](#license)

## Features

- **Ambient Lighting Control**: Adjust LED strip colors, brightness, and modes for vehicle doors (front/rear).
- **Fingerprint Authentication**: Vehicle unlock simulation using fingerprint status from VHAL.
- **HVAC Management**: Control fan speeds and temperatures for individual seats (driver, passenger, rear left/right).
- **Music Playback**: Stream and control music from Jamendo API, with search, playlists, and UI updates.
- **Navigation**: Real-time GPS-based mapping using Leaflet.js in a WebView, integrated with VHAL GPS properties.
- **OTA Updates**: Check for app updates from a GitHub repository, download, and install via PackageInstaller.
- **Voice Recognition**: Offline speech-to-text using Vosk, with commands for controlling HVAC, lights, music, etc.
- **Immersive UI**: Full-screen mode, animations (pulses, fades), dark/light theme toggling, and widget-based voice feedback.
- **VHAL Integration**: Centralized manager for vehicle properties like LED strips, HVAC fans, and temperatures.
- **Additional Utilities**: Battery, WiFi, Bluetooth status indicators; weather widget; fuel level display.

The app assumes a vehicle emulator or hardware with VHAL support (e.g., Android Automotive emulator in Android Studio).

## Architecture and File Explanations

The project is structured around activities for specific features, a main dashboard, and utility classes for services and managers. All code is in Kotlin, using AndroidX libraries for compatibility. Key components include:

### MainActivity.kt
- **Description**: The entry point and dashboard of the app. It handles the main UI layout, including cards for music, weather, fuel, and navigation shortcuts. It initializes the music service, voice widget, and update manager. Features include:
  - Dark/light mode toggling.
  - Volume controls with SeekBar.
  - Music controls (play/pause, next/previous) integrated with MusicService.
  - Voice FAB for triggering voice recognition.
  - Animations for car image (pulse, glow) and circle overlays.
  - OTA update notifications and periodic checks.
  - Integration with VhalManager for initial vehicle state.
- **Key Components**:
  - Uses `MusicService` for background music handling.
  - Manages mini voice widget for feedback (waveform, status text).
  - Handles permissions (audio recording) and voice command monitoring.
- **Dependencies**: Relies on `MusicService.kt`, `VhalManager.kt`, `VoiceWidgetManager.kt`, `UpdateManager.kt`.

### AmbientLightActivity.kt
- **Description**: Dedicated activity for controlling ambient LED lighting in the vehicle. Allows selection of modes (Warm, Cozy, Cool, Light, Night), brightness sliders for left/right sides, sync/off buttons, and VHAL property updates for LED strips.
  - Supports animations for light glow effects using GradientDrawable.
  - Integrates with VHAL via `CarPropertyManager` to set RGB values and brightness.
  - Handles door-specific areas (left/right doors).
- **Key Components**:
  - Mode buttons cycle through predefined colors.
  - SeekBars for brightness with real-time VHAL updates.
  - Glow layers for visual feedback on car image.
- **VHAL Integration**: Uses property `0x26400110` for LED control.

### FingerprintActivity.kt
- **Description**: Simulates fingerprint-based vehicle unlock. Plays a video loop (e.g., Volvo animation) and listens for fingerprint status changes via VHAL. On approval, animates unlock icon and transitions to MainActivity.
  - Uses ExoPlayer for video playback.
  - Registers callbacks for property changes.
- **Key Components**:
  - AnimatorSet for icon fade/scale animations.
  - ServiceConnection for Car API.
- **VHAL Integration**: Monitors property `0x21400107` for approved/refused status.

### HvacActivity.kt
- **Description**: Controls vehicle HVAC system. Features circular SeekBars for fan speeds per seat (driver, passenger, rear left/right), with GIF animations for active fans. Includes buttons for all max/off.
  - Maps UI speeds (0-5) to VHAL values (0-100).
  - Fade animations for GIF visibility.
- **Key Components**:
  - CircularSeekBar listeners update VHAL in real-time.
  - Glide for loading GIFs.
- **VHAL Integration**: Uses property `0x25400110` for fan speed; supports seat areas.

### MusicService.kt
- **Description**: Background service for music handling. Fetches tracks from Jamendo API (search, popular, tags), manages playback with MediaPlayer, and provides LiveData for UI updates (current track, position, playlist).
  - Supports play/pause, next/previous, seek, volume control.
  - Uses Retrofit for API calls and Picasso for album art.
- **Key Components**:
  - Coroutines for async operations.
  - Data classes for Track and Jamendo responses.
- **API Integration**: Jamendo API with client ID for free music streaming.

### NavigationActivity.kt
- **Description**: GPS-based navigation using WebView with Leaflet.js. Loads a local HTML map and updates marker position via VHAL GPS events.
  - Supports dark mode in WebView.
  - Reconnects to Car service on disconnect.
- **Key Components**:
  - WebChromeClient and WebViewClient for JS evaluation.
  - Callback for property changes.
- **VHAL Integration**: Listens to property `560005384` for latitude/longitude floats.

### UpdateManager.kt
- **Description**: Handles OTA updates. Periodically checks a GitHub JSON for new versions, downloads APK if available, verifies SHA-256 checksum, and installs via PackageInstaller.
  - Shows progress dialog during download/install.
- **Key Components**:
  - Thread for network operations.
  - PendingIntent for install callbacks.
- **Integration**: Uses GitHub raw file for update.json (version, URL, checksum).

### UpdatesActivity.kt
- **Description**: UI for managing pending updates. Displays current pending version (from SharedPreferences) and buttons to update now or cancel.
  - Simple ConstraintLayout with dynamic views.
  - Confirms actions via AlertDialog.
- **Key Components**:
  - Theme-aware UI updates.
  - Integrates with UpdateManager for installation.

### VhalManager.kt
- **Description**: Centralized utility for VHAL interactions. Initializes Car API, provides methods for HVAC (fan speed, temperature per seat), LED control (colors, brightness for front/rear doors), and status checks.
  - Defines seat and LED areas as constants.
  - Supports group operations (all seats, front/rear).
- **Key Components**:
  - Error handling for property availability and permissions.
  - Color packing for LED properties.
- **VHAL Properties**:
  - HVAC Fan: `0x25400110`
  - Temperature: `0x25400111`
  - LED Strip: `0x26400110`

### VoskDialogFragment.kt
- **Description**: Fragment for voice recognition using Vosk. Handles model loading/extraction, speech service, and UI states (idle, listening, text display). Integrates with ViewModel for shared state.
  - Waveform visualization during listening.
  - Processes recognized text for commands (e.g., HVAC, lights).
  - Supports landscape/portrait layouts.
- **Key Components**:
  - RecognitionListener implementation.
  - Coroutines for model init and timeouts.
  - SharedPreferences for ambient color.
- **Integration**: Uses Vosk model from assets; commands trigger VhalManager actions.



---