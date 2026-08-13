# Production ProGuard rules for com.safa.account

# --- Obfuscation ---
-repackageclasses ''
-keepattributes Signature,*Annotation*,Exceptions,InnerClasses

# --- Data models and API DTOs ---
# Business persistence uses the encrypted LocalFirstStore/SQLiteOpenHelper
# implementation. Room and SQLCipher are not production runtime dependencies.
-keep class com.safa.account.data.model.** { *; }
-keep class com.safa.account.data.api.dto.** { *; }

# --- Retrofit + Moshi ---
# DTO adapters are generated with Moshi codegen; keep the JSON contract and
# generated/annotated adapter surface used by Retrofit.
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
