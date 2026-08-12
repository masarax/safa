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
  defaultConfig {
    applicationId = "com.safa.account"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      val storePassword = System.getenv("STORE_PASSWORD")
      val keyAlias = System.getenv("KEY_ALIAS")
      val keyPassword = System.getenv("KEY_PASSWORD")
      if (!keystorePath.isNullOrBlank() && !storePassword.isNullOrBlank() && !keyAlias.isNullOrBlank() && !keyPassword.isNullOrBlank()) {
        val keystoreFile = file(keystorePath)
        if (!keystoreFile.exists()) throw GradleException("Release keystore not found: $keystorePath")
        storeFile = keystoreFile
        this.storePassword = storePassword
        this.keyAlias = keyAlias
        this.keyPassword = keyPassword
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(platform(libs.androidx.compose.bom)); implementation(platform(libs.firebase.bom)); implementation(libs.androidx.activity.compose); implementation(libs.androidx.biometric)
  implementation(libs.androidx.compose.material.icons.core); implementation(libs.androidx.compose.material.icons.extended); implementation(libs.androidx.compose.material3); implementation(libs.androidx.compose.ui); implementation(libs.androidx.compose.ui.graphics); implementation(libs.androidx.compose.ui.tooling.preview); implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose); implementation(libs.androidx.lifecycle.runtime.ktx); implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation("androidx.security:security-crypto:1.1.0-alpha06"); implementation(libs.androidx.work.runtime.ktx); implementation(libs.coil.compose); implementation(libs.converter.moshi); implementation(libs.kotlinx.coroutines.android); implementation(libs.kotlinx.coroutines.core); implementation(libs.logging.interceptor); implementation(libs.moshi.kotlin); implementation(libs.okhttp); implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4); testImplementation(libs.androidx.core); testImplementation(libs.androidx.junit); testImplementation(libs.junit); testImplementation(libs.kotlinx.coroutines.test); testImplementation(libs.mockito.kotlin); testImplementation(libs.robolectric); testImplementation(libs.roborazzi); testImplementation(libs.roborazzi.compose); testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom)); androidTestImplementation(libs.androidx.compose.ui.test.junit4); androidTestImplementation(libs.androidx.espresso.core); androidTestImplementation(libs.androidx.junit); androidTestImplementation(libs.androidx.runner); debugImplementation(libs.androidx.compose.ui.test.manifest); debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.moshi.kotlin.codegen)
}

// Release gate: CI must compile tests and the debug artifact before a production release.
