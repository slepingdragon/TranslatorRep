# ProGuard rules for TranslatorRep (release builds).
#
# Currently isMinifyEnabled = false in release; this file is the
# placeholder for when we flip minification on (probably after Epic 4
# stabilizes, before TestFlight Ad Hoc-equivalent APK distribution).
#
# When enabling:
# 1. Keep LiveKit native classes (WebRTC JNI):
#      -keep class io.livekit.android.** { *; }
#      -keep class org.webrtc.** { *; }
# 2. Keep Firebase model classes:
#      -keep class com.google.firebase.** { *; }
# 3. Keep Room generated DAOs (KSP-generated):
#      -keep class androidx.room.** { *; }
# 4. Keep our `AllowedLogKey` enum (SafeLog facade reflection-safe):
#      -keep enum com.xaeryx.translatorrep.logging.AllowedLogKey { *; }
# 5. Keep our Data Channel payload data class (JSON serialization-safe):
#      -keep class com.xaeryx.translatorrep.datachannel.** { *; }
# 6. Keep ULID library (varies per chosen lib at Story 1.5).
