# Production ProGuard rules for com.safa.account

# --- Obfuscation ---
-repackageclasses ''
-keepattributes Signature,*Annotation*,Exceptions,InnerClasses

# --- Data models and API DTOs ---
-keep class com.safa.account.data.model.** { *; }
-keep class com.safa.account.data.api.dto.** { *; }

# --- Room compatibility layer ---
# Remove these rules together with the remaining Room source/dependencies once
# the LocalFirstStore-only persistence migration is complete.
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

# --- Biometric ---
-keep class androidx.biometric.** { *; }

# --- Compose ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# --- AndroidX Security ---
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**

# Preserve line numbers for production crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
