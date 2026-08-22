plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

val safaVersionCode = providers.gradleProperty("SAFA_VERSION_CODE")
  .orNull?.toIntOrNull()
  ?: throw GradleException("SAFA_VERSION_CODE must be a positive integer in gradle.properties")
val safaVersionName = providers.gradleProperty("SAFA_VERSION_NAME")
  .orNull?.trim()?.takeIf { it.isNotBlank() }
  ?: throw GradleException("SAFA_VERSION_NAME must be set in gradle.properties")
if (safaVersionCode <= 1) {
  throw GradleException("SAFA_VERSION_CODE must be greater than the legacy release code 1")
}

val releaseKeystorePath = System.getenv("KEYSTORE_PATH")?.trim().orEmpty()
val releaseStorePassword = System.getenv("STORE_PASSWORD")?.trim().orEmpty()
val releaseKeyAlias = System.getenv("KEY_ALIAS")?.trim().orEmpty()
val releaseKeyPassword = System.getenv("KEY_PASSWORD")?.trim().orEmpty()
val releaseSigningConfigured = listOf(
  releaseKeystorePath,
  releaseStorePassword,
  releaseKeyAlias,
  releaseKeyPassword
).all { it.isNotBlank() }

android {
  namespace = "com.safa.account"
  compileSdk { version = release(36) { minorApiLevel = 1 } }
  defaultConfig {
    applicationId = "com.safa.account"
    minSdk = 24
    targetSdk = 36
    versionCode = safaVersionCode
    versionName = safaVersionName
    buildConfigField("int", "SAFA_RELEASE_VERSION_CODE", safaVersionCode.toString())
    buildConfigField("String", "SAFA_RELEASE_VERSION_NAME", "\"$safaVersionName\"")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  signingConfigs {
    if (releaseSigningConfigured) {
      create("release") {
        val keystoreFile = file(releaseKeystorePath)
        if (!keystoreFile.exists()) throw GradleException("Release keystore not found: $releaseKeystorePath")
        storeFile = keystoreFile
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
    }
  }
  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // A plain local `./gradlew assembleRelease` must be able to produce an
      // unsigned release artifact. CI/production remains signed whenever the
      // complete secret-backed signing environment is provided.
      if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
    }
    debug { }
  }
  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures { compose = true; buildConfig = true }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

secrets { propertiesFileName = ".env"; defaultPropertiesFileName = ".env.example" }

dependencies {
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.3")
  implementation(platform(libs.androidx.compose.bom)); implementation(libs.androidx.activity.compose); implementation(libs.androidx.biometric)
  implementation(libs.androidx.compose.material.icons.core); implementation(libs.androidx.compose.material.icons.extended); implementation(libs.androidx.compose.material3); implementation(libs.androidx.compose.ui); implementation(libs.androidx.compose.ui.graphics); implementation(libs.androidx.compose.ui.tooling.preview); implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose); implementation(libs.androidx.lifecycle.runtime.ktx); implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation("androidx.security:security-crypto:1.1.0"); implementation(libs.androidx.work.runtime.ktx); implementation(libs.androidx.datastore.preferences); implementation(libs.coil.compose); implementation(libs.converter.moshi); implementation(libs.kotlinx.coroutines.android); implementation(libs.kotlinx.coroutines.core); implementation(libs.logging.interceptor); implementation(libs.moshi.kotlin); implementation(libs.okhttp); implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4); testImplementation(libs.androidx.core); testImplementation(libs.androidx.junit); testImplementation(libs.junit); testImplementation(libs.kotlinx.coroutines.test); testImplementation(libs.mockito.kotlin); testImplementation(libs.robolectric); testImplementation(libs.roborazzi); testImplementation(libs.roborazzi.compose); testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom)); androidTestImplementation(libs.androidx.compose.ui.test.junit4); androidTestImplementation(libs.androidx.espresso.core); androidTestImplementation(libs.androidx.junit); androidTestImplementation(libs.androidx.runner); debugImplementation(libs.androidx.compose.ui.test.manifest); debugImplementation(libs.androidx.compose.ui.tooling)
  ksp(libs.moshi.kotlin.codegen)
}

// Resolve the exact release runtime graph into a machine-readable coordinate
// list. Security CI consumes this list for OSV advisory scanning and SBOM
// generation, so catalog text alone can never hide a transitive dependency.
tasks.register("safaResolvedReleaseDependencies") {
  group = "verification"
  description = "Writes the resolved release runtime Maven dependency graph."
  val outputFile = layout.buildDirectory.file("reports/security/release-dependencies.txt")
  outputs.file(outputFile)
  doLast {
    val coordinates = configurations.getByName("releaseRuntimeClasspath")
      .incoming.resolutionResult.allComponents
      .mapNotNull { component ->
        component.moduleVersion?.let { module ->
          "${module.group}:${module.name}:${module.version}"
        }
      }
      .distinct()
      .sorted()
    if (coordinates.isEmpty()) {
      throw GradleException("Resolved release dependency graph is empty")
    }
    val destination = outputFile.get().asFile
    destination.parentFile.mkdirs()
    destination.writeText(coordinates.joinToString(separator = "\n", postfix = "\n"))
    println("Wrote ${coordinates.size} resolved release dependencies to ${destination.path}")
  }
}

// Release gate: CI validates unit tests, lint, minified release packaging and
// emulator-backed instrumentation against the same property-driven identity.
