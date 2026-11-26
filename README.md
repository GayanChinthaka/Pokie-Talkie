# Pokie Talkie 📻

A private, decentralized family communication Android application built with **Kotlin** and **Jetpack Compose** (Material 3). Designed for zero-internet-data local communication over **Wi-Fi LAN** and **Bluetooth RFCOMM**, backed by **Firebase Cloud Messaging (FCM v1)** for background wake-up and remote signaling, and **Android TelecomManager / ConnectionService** for native incoming call handling.

---

## 🌟 Key Architecture & Features

### 1. 📡 Multi-Transport Connectivity (Wi-Fi LAN, Bluetooth & FCM v1)
- **Wi-Fi / Bluetooth Mode Switch**: Toggle seamlessly between **Wi-Fi Mode** and **Bluetooth Mode** directly from the home top bar.
- **Zero-Config LAN Discovery (`NsdManager`)**: Automatically advertises and discovers family devices on the local Wi-Fi subnet (`_pokietalkie._tcp.`) and exchanges direct TCP payloads on port `52220` (`LanSocketServer`).
- **Offline Bluetooth RFCOMM Mesh**: Sends chat messages, delivery/read receipts, and call signaling directly over Bluetooth RFCOMM (`BluetoothTransportManager`) and streams full-duplex 16 kHz PCM walkie-talkie voice (`BluetoothVoiceManager`) with zero router or internet required.
- **FCM v1 Remote Wake-Up & Signaling**: Uses `FcmV1Client` with OAuth2 service-account authentication and GZIP SDP compression (`SdpCompressor`) to wake sleeping devices and deliver signaling when peers are outside direct LAN reach.
- **Connectivity Monitoring**: Monitors Wi-Fi, internet availability, and Bluetooth adapter state via `ConnectivityMonitor`, pausing active mDNS scanning in the background to conserve battery.

### 2. 📞 Native Calling, WebRTC Video & Screen Sharing
- **Telecom `ConnectionService` (`PokieConnectionService`)**: Registers a self-managed `PhoneAccount` with `TelecomManager` and falls back to a lock-screen-capable full-screen `IncomingCallActivity` with swipe-to-answer/decline controls.
- **Zero-Data LAN WebRTC (`io.getstream:stream-webrtc-android`)**: Hardware-accelerated voice and video calling with Opus audio, echo cancellation, noise suppression, camera flip, mid-call audio-to-video upgrade negotiation, and live **Screen Sharing** (`MediaProjection` via `ScreenShareCapturer`).
- **Smart Audio Routing & Feedback**: Supports Bluetooth headsets, wired/USB headsets, speakerphone, and phone earpiece (`WebRtcAudioDeviceManager`) with automatic ear-proximity screen lock (`ProximityLock`), looping ringtones, vibration patterns, outgoing ringback tone, and SoundPool connect/disconnect tones (`RingtoneVibrationManager`).

### 3. 💬 Local-First Family Chat
- **Instant Messaging**: Delivers over direct LAN TCP or Bluetooth RFCOMM, falling back to FCM v1 when in Wi-Fi mode.
- **Receipts & Unread Badges**: Tracks `SENDING`, `SENT_LAN`, `SENT_FCM`, `DELIVERED`, `READ`, and `FAILED` states with live unread counts.
- **On-Device Persistence**: Stores contacts and messages locally in **Room SQLite** (`PokieDatabase`, schema v3).

### 4. 🎨 Material 3 Expressive Settings & Customization
- **Profile Management**: Custom display name, user ID copy, and local profile photo picker (`SettingsScreen`).
- **Appearance (`AppearanceSettingsScreen`)**: System / Light / Dark theme modes, 6 brand accent color palettes, and 8 avatar fallback colors.
- **Ringing & Sounds (`RingingSettingsScreen`)**: Granular toggles for incoming call ringtone, call vibration, outgoing ringback tone, connect/disconnect tones, message sound, and message vibration.
- **Software Updater & About (`UpdateScreen`, `AboutScreen`)**: GitHub Releases update checker (`Updater`), auto-check toggle, and architecture overview.
- **Developer Preview Hub (Debug Builds)**: Built-in single-device UI sandbox (`DevPreviewHubScreen`, `DevMockCallScreen`) gated by `DevConfig.IS_DEV_MODE` (`BuildConfig.DEBUG`).

---

## 📁 Package Structure

```text
com.pokerlanka.pokie_talkie/
├── PokieApp.kt                                 # Application entry & foreground lifecycle tracking
├── MainActivity.kt                             # Root Activity, permissions, & Compose host
├── bluetooth/
│   ├── BluetoothTransportManager.kt            # RFCOMM P2P messaging & signaling server/client
│   └── BluetoothVoiceManager.kt                # Full-duplex 16kHz PCM voice streaming over RFCOMM
├── data/
│   ├── local/
│   │   ├── PokieDatabase.kt                    # Room Database (v3) & TypeConverters
│   │   ├── UserPreferencesManager.kt           # SharedPreferences + StateFlows for settings & identity
│   │   ├── dao/Daos.kt                         # MessageDao, ContactDao
│   │   └── entity/Entities.kt                  # MessageEntity, ContactEntity
│   ├── model/Models.kt                         # Domain models, SignalingType/Payload, CallState
│   └── repository/Repositories.kt              # ChatRepository, ContactRepository, CallRepository
├── di/
│   └── AppContainer.kt                         # Manual DI container & signaling/call orchestration
├── fcm/
│   ├── FcmSignalingClient.kt                   # LAN / FCM v1 / Bluetooth signaling router
│   ├── FcmV1Client.kt                          # HTTP v1 FCM client with OAuth2 JWT signing
│   ├── PokieFirebaseMessagingService.kt        # FCM push receiver & payload decompression
│   └── SdpCompressor.kt                        # GZIP + Base64 SDP compression for 4KB FCM limit
├── lan/
│   ├── ConnectivityMonitor.kt                  # Wi-Fi, Wi-Fi Direct, & Bluetooth state monitor
│   ├── LanDiscoveryManager.kt                  # NSD / mDNS peer advertisement & resolution
│   └── LanSocketServer.kt                      # TCP socket server/client for LAN signaling & chat
├── service/
│   └── PokieLocalService.kt                    # Foreground service (connectedDevice/call/mic/cam/projection)
├── telecom/
│   ├── CallActionReceiver.kt                   # Notification Decline/Hangup/Stop-Share actions
│   ├── CallController.kt                       # Call lifecycle, 45s ring timeout, proximity lock
│   ├── PokieConnection.kt                      # Telecom Connection callbacks
│   ├── PokieConnectionService.kt               # Android ConnectionService implementation
│   ├── ProximityLock.kt                        # Earpiece proximity screen-off WakeLock
│   ├── RingtoneVibrationManager.kt             # Ringtones, vibration, ringback, & SoundPool cues
│   └── TelecomCallManager.kt                   # PhoneAccount registration & call notifications
├── ui/
│   ├── call/                                   # CallScreen, CallViewModel, IncomingCallActivity
│   ├── chat/                                   # ChatScreen, ChatViewModel, StickerPicker
│   ├── components/                             # AvatarView, ChatBubble, Material3SettingsGroup, swipe call UI
│   ├── dev/                                    # DevConfig, DevPreviewHubScreen, DevMockCallScreen
│   ├── home/                                   # HomeScreen, HomeViewModel, ConnectionModeSwitch
│   ├── navigation/NavGraph.kt                  # Jetpack Compose Navigation graph
│   ├── settings/                               # Settings, Appearance, Ringing, Updates, About
│   └── theme/                                  # Color, Theme, Type
├── utils/
│   └── Updater.kt                              # GitHub Releases API check & version comparison
└── webrtc/
    ├── ScreenShareCapturer.kt                  # MediaProjection screen capture wrapper
    ├── SurfaceViewRendererComposable.kt        # Compose wrapper for WebRTC VideoTrack
    ├── WebRtcAudioDeviceManager.kt             # Bluetooth / Headset / Speaker / Earpiece routing
    └── WebRtcManager.kt                        # PeerConnection, camera, renegotiation, & ICE handling
```

---

## 🛠️ Requirements & Setup

1. **Android Studio**: Iguana / Jellyfish or newer with **JDK 17** (Gradle `8.4`, AGP `8.3.2`, Kotlin `1.9.22`).
2. **Android SDK**:
   - `minSdk = 26` (Android 8.0 Oreo)
   - `compileSdk = 34` / `targetSdk = 34` (Android 14)
   - Target ABIs: `arm64-v8a`, `armeabi-v7a`
3. **Firebase Setup**:
   - Create a project in [Firebase Console](https://console.firebase.google.com/) and register an Android app with package name `com.pokerlanka.pokie_talkie`.
   - Place your `google-services.json` in `app/google-services.json`.
   - For direct peer-to-peer **FCM HTTP v1** wake-up pushes, place a Firebase Service Account JSON at `app/src/main/res/raw/firebase_service_account.json` (do **not** commit production credentials to public repositories).

---

## 🚀 Building & Testing

Open the project directory in **Android Studio** and click **Run**, or use the Gradle wrapper from the command line:

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

> **Note**: Currently `app/build.gradle.kts` configures `release` builds with the `debug` signing config and `isMinifyEnabled = false`. Configure your own release keystore before distributing production builds.
