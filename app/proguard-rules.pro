# Production ProGuard rules for com.safa.account

# --- Obfuscation ---
-repackageclasses ''
-flattenpackagehierarchy ''
-keepattributes Signature,*Annotation*,Exceptions,InnerClasses

# --- Data models (Room entities & API DTOs must not be obfuscated) ---
-keep class com.safa.account.data.model.** { *; }
-keep class com.safa.account.data.api.dto.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# --- Retrofit + Moshi ---
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keepclassmembers @com.squareup.moshi.JsonClass class * { *; }

# --- OkHttp + Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# --- Kotlin coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# --- Suppress all Log calls in release ---
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# --- Biometric ---
-keep class androidx.biometric.** { *; }

# --- Compose (required for reflection-based tooling) ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Preserve line numbers for production crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
