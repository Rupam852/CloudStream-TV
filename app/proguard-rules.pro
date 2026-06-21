# CloudStream TV — ProGuard / R8 Rules
# These rules prevent R8 from stripping or renaming classes that are used
# reflectively, via JNI, or by external libraries at runtime.

# ─── Keep app entry point ──────────────────────────────────────────────────
-keep class com.cloudstream.tv.MainActivity { *; }

# ─── Kotlin / Coroutines ───────────────────────────────────────────────────
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ─── Jetpack Compose / TV ─────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-keep class androidx.tv.** { *; }
-dontwarn androidx.compose.**
-dontwarn androidx.tv.**

# ─── Media3 / ExoPlayer ───────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ─── OkHttp ───────────────────────────────────────────────────────────────
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# ─── Gson ────────────────────────────────────────────────────────────────
# Keep all data classes used in JSON deserialization
-keepclassmembers class com.cloudstream.tv.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.cloudstream.tv.network.** { *; }
-keep class com.cloudstream.tv.data.** { *; }
-dontwarn com.google.gson.**

# Prevent obfuscating generic type tokens and signatures
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class * extends com.google.gson.reflect.TypeToken {
    <init>(...);
}

# ─── Coil ────────────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ─── Android Audio ───────────────────────────────────────────────────────
-keep class android.media.audiofx.** { *; }

# ─── Prevent stripping of reflection-accessed members ────────────────────
-keepattributes SourceFile, LineNumberTable
