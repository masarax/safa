# Production ProGuard rules for com.safa.account

# --- Obfuscation ---
-repackageclasses ''
-keepattributes Signature,*Annotation*,Exceptions,InnerClasses

# --- Data models and API DTOs ---
# Business persistence uses the encrypted LocalFirstStore/SQLiteOpenHelper
# implementation. Room and SQLCipher are not SAFA business persistence dependencies.
-keep class com.safa.account.data.model.** { *; }
-keep class com.safa.account.data.api.dto.** { *; }

# WorkManager is a runtime dependency and internally uses a generated Room
# database implementation. Its no-arg constructor is reflectively instantiated
# during AndroidX Startup, so R8 must retain that constructor. This rule is
# intentionally scoped to WorkManager and does not make Room/SQLCipher part of
# SAFA's business persistence architecture.
-keep class androidx.work.impl.WorkDatabase_Impl {
    <init>();
}

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
