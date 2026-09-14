# Proguard rules for Pokie Talkie

# WebRTC Native
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Data Models & Entities (Gson + Room)
-keep class com.pokerlanka.pokie_talkie.data.model.** { *; }
-keepclassmembers class com.pokerlanka.pokie_talkie.data.model.** { *; }
-keep class com.pokerlanka.pokie_talkie.data.local.entity.** { *; }
-keepclassmembers class com.pokerlanka.pokie_talkie.data.local.entity.** { *; }
-keep class com.pokerlanka.pokie_talkie.data.local.dao.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Coroutines
-dontwarn kotlinx.coroutines.**
