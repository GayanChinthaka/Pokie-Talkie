# Proguard rules for Pokie Talkie
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

-keep class com.pokerlanka.pokie_talkie.data.model.** { *; }
-keepclassmembers class com.pokerlanka.pokie_talkie.data.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
