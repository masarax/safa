plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.safa.account"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  defaultConfig { applicationId = "com.safa.account"; minSdk = 24; targetSdk = 36; versionCode = 1; versionName = "1.0"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
  sourceSets { getByName("main") { java.exclude("**/data/dao/**") } }
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val keystoreFile = file(keystorePath)
      if (keystoreFile.exists() && System.getenv("STORE_PASSWORD") != null) { storeFile=keystoreFile; storePassword=System.getenv("STORE_PASSWORD"); keyAlias=System.getenv("KEY_ALIAS") ?: "upload"; keyPassword=System.getenv("KEY_PASSWORD") }
      else { val debugKeystore=file("${System.getProperty("user.home")}/.android/debug.keystore"); if(debugKeystore.exists()){storeFile=debugKeystore;storePassword="android";keyAlias="androiddebugkey";keyPassword="android"} }
    }
  }
  buildTypes { release { isCrunchPngs=false; isMinifyEnabled=true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro"); signingConfig=signingConfigs.getByName("release") }; debug { } }
  compileOptions { sourceCompatibility=JavaVersion.VERSION_11; targetCompatibility=JavaVersion.VERSION_11 }
  buildFeatures { compose=true; buildConfig=true }
  testOptions { unitTests { isIncludeAndroidResources=true } }
}
secrets { propertiesFileName=".env"; defaultPropertiesFileName=".env.example" }

dependencies {
  implementation(platform(libs.androidx.compose.bom)); implementation(platform(libs.firebase.bom)); implementation(libs.androidx.activity.compose); implementation(libs.androidx.biometric)
  implementation(libs.androidx.compose.material.icons.core); implementation(libs.androidx.compose.material.icons.extended); implementation(libs.androidx.compose.material3); implementation(libs.androidx.compose.ui); implementation(libs.androidx.compose.ui.graphics); implementation(libs.androidx.compose.ui.tooling.preview); implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose); implementation(libs.androidx.lifecycle.runtime.ktx); implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation("androidx.security:security-crypto:1.1.0-alpha06"); implementation(libs.coil.compose); implementation(libs.converter.moshi); implementation(libs.kotlinx.coroutines.android); implementation(libs.kotlinx.coroutines.core); implementation(libs.logging.interceptor); implementation(libs.moshi.kotlin); implementation(libs.okhttp); implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4); testImplementation(libs.androidx.core); testImplementation(libs.androidx.junit); testImplementation(libs.junit); testImplementation(libs.kotlinx.coroutines.test); testImplementation(libs.mockito.kotlin); testImplementation(libs.robolectric); testImplementation(libs.roborazzi); testImplementation(libs.roborazzi.compose); testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom)); androidTestImplementation(libs.androidx.compose.ui.test.junit4); androidTestImplementation(libs.androidx.espresso.core); androidTestImplementation(libs.androidx.junit); androidTestImplementation(libs.androidx.runner); debugImplementation(libs.androidx.compose.ui.test.manifest); debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.moshi.kotlin.codegen)
}
