package com.safa.account

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceProfileContractTest {
    private fun rootFile(path: String): File {
        val candidates = listOf(File(path), File("../$path"), File("../../$path"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Repository file not found: $path")
    }

    @Test
    fun releaseOwnsCheckedInBaselineProfileAndDedicatedGeneratorModule() {
        val settings = rootFile("settings.gradle.kts").readText()
        val rootGradle = rootFile("build.gradle.kts").readText()
        val catalog = rootFile("gradle/libs.versions.toml").readText()
        val appGradle = rootFile("app/build.gradle.kts").readText()
        val benchmarkGradle = rootFile("benchmark/build.gradle.kts").readText()
        val benchmarkSource = rootFile("benchmark/src/main/java/com/safa/account/benchmark/SafaPerformanceBenchmark.kt").readText()
        val profile = rootFile("app/src/main/baseline-prof.txt").readText()
        val docs = rootFile("docs/ANDROID_PERFORMANCE.md").readText()

        assertTrue(settings.contains("include(\":benchmark\")"))
        assertFalse("Current AGP line must not declare the incompatible Baseline Profile Gradle plugin", rootGradle.contains("baseline.profile"))
        assertFalse(catalog.contains("androidx.baselineprofile"))
        assertFalse(catalog.contains("baselineprofile"))
        assertFalse(appGradle.contains("baselineProfile(project(\":benchmark\"))"))
        assertTrue(appGradle.contains("create(\"benchmark\")"))
        assertTrue(appGradle.contains("isDebuggable = false"))
        assertTrue("AGP is already provided by the root build; benchmark must use the unversioned test plugin", benchmarkGradle.contains("id(\"com.android.test\")"))
        assertFalse(benchmarkGradle.contains("libs.plugins.android.test"))
        assertFalse(benchmarkGradle.contains("baselineprofile"))
        assertTrue(benchmarkGradle.contains("androidx.benchmark.macro"))
        assertTrue(benchmarkGradle.contains("targetProjectPath = \":app\""))
        assertTrue(benchmarkSource.contains("BaselineProfileRule"))
        assertTrue(benchmarkSource.contains("baselineProfileRule.collect"))
        assertTrue(docs.contains("SafaBaselineProfileGenerator"))
        assertFalse(docs.contains(":app:generateBaselineProfile"))
        listOf(
            "MainActivity",
            "data/local",
            "data/repository",
            "data/sync",
            "ui/screens",
            "ui/viewmodel",
        ).forEach { expected -> assertTrue("Baseline profile missing $expected", profile.contains(expected)) }
    }

    @Test
    fun controlledDeviceBudgetsAndLargeSyntheticFixtureAreVersioned() {
        val budgets = rootFile("benchmark/performance-budgets.json").readText()
        val fixtureClient = rootFile("benchmark/src/main/java/com/safa/account/benchmark/BenchmarkFixture.kt").readText()
        val fixtureProvider = rootFile("app/src/benchmark/java/com/safa/account/benchmark/BenchmarkFixtureProvider.kt").readText()
        val benchmarkHost = rootFile("app/src/benchmark/java/com/safa/account/benchmark/BenchmarkHostActivity.kt").readText()
        val benchmarkManifest = rootFile("app/src/benchmark/AndroidManifest.xml").readText()
        val benchmark = rootFile("benchmark/src/main/java/com/safa/account/benchmark/SafaPerformanceBenchmark.kt").readText()
        val docs = rootFile("docs/ANDROID_PERFORMANCE.md").readText()
        val ci = rootFile(".github/workflows/android-ci.yml").readText()

        listOf(
            "cold_ms_p50_max",
            "cold_ms_p95_max",
            "warm_ms_p50_max",
            "frame_ms_p95_max",
            "jank_percent_max",
            "max_percent_vs_accepted_baseline",
        ).forEach { key -> assertTrue("Missing performance budget $key", budgets.contains(key)) }

        assertTrue(fixtureProvider.contains("CUSTOMER_COUNT = 400"))
        assertTrue(fixtureProvider.contains("TRANSACTION_COUNT = 1_200"))
        assertTrue(fixtureProvider.contains("LocalFirstStore(appContext)"))
        assertTrue(fixtureClient.contains("content://\$AUTHORITY"))
        assertTrue(benchmarkManifest.contains("BenchmarkFixtureProvider"))
        assertTrue(benchmarkManifest.contains("BenchmarkHostActivity"))

        assertTrue(benchmarkHost.contains("SafaViewModelFactory(repository, tokenManager = null)"))
        assertTrue(benchmarkHost.contains("restoreAuthenticatedSession"))
        assertTrue(benchmarkHost.contains("setLanguage(\"EN\")"))
        listOf(
            "can_view_customers",
            "can_view_suppliers",
            "can_view_transactions",
            "can_manage_wallet",
        ).forEach { permission ->
            assertTrue("Benchmark operator missing permission: $permission", benchmarkHost.contains(permission))
        }
        listOf(
            "DashboardScreen",
            "CustomerScreen",
            "SupplierScreen",
            "TransactionScreen",
            "WalletScreen",
        ).forEach { screen ->
            assertTrue("Benchmark host must render production screen: $screen", benchmarkHost.contains(screen))
        }

        assertTrue(benchmark.contains("StartupTimingMetric"))
        assertTrue(benchmark.contains("FrameTimingMetric"))
        assertTrue(benchmark.contains("startBenchmarkHost"))
        listOf("Transactions", "Customers", "Suppliers", "Wallet").forEach { destination ->
            assertTrue("Benchmark journey missing required destination: $destination", benchmark.contains("\"$destination\""))
        }
        assertFalse("Required benchmark destinations must fail instead of silently skipping", benchmark.contains("if (target != null)"))
        assertFalse("Required benchmark destinations must not use optional first-match navigation", benchmark.contains("firstOrNull()"))

        assertTrue(docs.contains("shared hosted emulators"))
        assertTrue(docs.contains("check-performance-budget.py"))
        assertTrue(docs.contains("SafaMacrobenchmark"))
        assertTrue(docs.contains("normalized-results.json"))
        assertFalse(docs.contains("check-performance-budget.py benchmark/performance-budgets.json"))
        assertTrue(ci.contains("./gradlew --no-daemon :app:connectedDebugAndroidTest"))
        assertFalse(ci.contains("./gradlew --no-daemon connectedDebugAndroidTest"))
    }
}
