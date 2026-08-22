plugins {
  alias(libs.plugins.android.test)
}

android {
  namespace = "com.safa.account.benchmark"
  compileSdk { version = release(37) }

  defaultConfig {
    minSdk = 28
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Emulator timing is intentionally not a merge gate; controlled reference
    // devices remain the source of accepted performance measurements.
    testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
  }

  targetProjectPath = ":app"
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
  implementation(libs.androidx.benchmark.macro.junit4)
  implementation(libs.androidx.test.uiautomator)
  implementation(libs.androidx.junit)
}
