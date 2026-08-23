# Pokie Talkie 📻

A modern WhatsApp-like family communication Android application built with **Kotlin** and **Jetpack Compose**. Designed for high performance, LAN-constrained zero-internet-data peer-to-peer audio/video streaming over local WiFi, backed by **Firebase Cloud Messaging (FCM)** for background wake-up and WebRTC signaling, and **Android TelecomManager / ConnectionService** for native incoming call UI.

---

## 🌟 Key Architecture & Features

### 1. 🚀 Background Stability & Native Incoming Call UI (Telecom ConnectionService)
- Implements `ConnectionService` (`PokieConnectionService`) and registers a `PhoneAccount` with `TelecomManager`.
- When an incoming call FCM high-priority payload is received (or via direct LAN socket), `TelecomManager.addNewIncomingCall` is immediately invoked.
- Displays the native Android incoming call screen even when the device is locked, asleep, or in Doze mode.
- Includes fallback to full-screen intent activity (`IncomingCallActivity`) if Telecom self-managed permissions are restricted.

### 2. ⚡ Zero-Data LAN WebRTC Video & Voice Calls
- **WebRTC PeerConnectionFactory** with hardware video encoders and decoders (`io.getstream:stream-webrtc-android`).
- **LAN ICE Prioritization**: Configured with host/mDNS candidate prioritization, ensuring video and audio packets flow exclusively over the local WiFi subnet (0 bytes of mobile data / internet bandwidth consumed for calls).
- Supports video PIP thumbnail previews, camera switching (Front/Rear), microphone mute/unmute, and speakerphone/earpiece routing.

### 3. 💬 Family Chat & Stickers (Room DB + Direct Sockets)
- WhatsApp-styled chat UI built in **Jetpack Compose** with Material 3.
- Fast delivery: Uses local direct TCP sockets if both family members are on the same WiFi network, falling back seamlessly to FCM Cloud data payloads if the recipient is in the background.
- Rich sticker packs (Family Joy, Fun Reactions, Walkie Alerts) and emojis.
- Full local message persistence via **Room Database**.

### 4. 🔍 Zero-Config LAN Discovery (mDNS / NSD)
- Automatic discovery using Android's `NsdManager` (`_pokietalkie._tcp`).
- Discovers family members on the same subnet, dynamically resolving their IP addresses and socket ports.

---

## 📁 Package Structure

```
com.pokerlanka.pokie_talkie/
├── PokieApp.kt                               # Application entry, channels, & initialization
├── MainActivity.kt                           # Root Activity with runtime permissions & Compose
├── data/
│   ├── model/Models.kt                       # Domain models, Signaling payloads, Call types
│   ├── local/
│   │   ├── PokieDatabase.kt                  # Room Database instance & TypeConverters
│   │   ├── dao/Daos.kt                       # MessageDao, ContactDao, CallLogDao
│   │   └── entity/Entities.kt                # MessageEntity, ContactEntity, CallLogEntity
│   └── repository/Repositories.kt            # ChatRepository, ContactRepository, CallRepository
├── lan/
│   ├── LanDiscoveryManager.kt                # NSD / mDNS discovery for family on local WiFi
│   └── LanSocketServer.kt                    # Coroutine TCP socket server for instant LAN messaging
├── fcm/
│   ├── PokieFirebaseMessagingService.kt      # FCM high-priority payload receiver & wake-up
│   └── FcmSignalingClient.kt                 # Dual LAN / FCM signaling dispatcher
├── telecom/
│   ├── PokieConnectionService.kt             # Android ConnectionService
│   ├── PokieConnection.kt                    # Telecom Connection instance (answer, reject, hangup)
│   └── TelecomCallManager.kt                 # PhoneAccount registration & call invoker
├── webrtc/
│   ├── WebRtcManager.kt                      # WebRTC engine (Offer, Answer, LAN ICE, tracks)
│   ├── WebRtcAudioDeviceManager.kt           # Speaker, earpiece, and mic mute routing
│   └── SurfaceViewRendererComposable.kt      # Compose wrapper for WebRTC VideoTrack
├── di/
│   └── AppContainer.kt                       # Dependency injection & service locator
└── ui/
    ├── theme/                                # Color, Theme, Type
    ├── components/CommonComponents.kt        # Avatars, LAN badges, Chat bubbles, Call buttons
    ├── navigation/NavGraph.kt                # Compose Navigation graph
    ├── home/                                 # Family contact list & search
    ├── chat/                                 # Chat room, sticker picker, instant messaging
    └── call/                                 # WebRTC video/voice call screen & incoming activity
```

---

## 🛠️ Requirements & Setup

1. **Android Studio**: Android Studio Hedgehog / Iguana / Jellyfish (JDK 17).
2. **Minimum SDK**: Android API 26 (Android 8.0 Oreo).
3. **Target SDK**: Android API 34 (Android 14).
4. **Firebase Setup**:
   - Create a project in [Firebase Console](https://console.firebase.google.com/).
   - Add an Android App with package name `com.pokerlanka.pokie_talkie`.
   - Download your `google-services.json` and replace `app/google-services.json`.

---

## 🚀 Building & Running

Open the project directory in **Android Studio** and click **Run**.
Or via command line:
```bash
./gradlew assembleDebug
```
