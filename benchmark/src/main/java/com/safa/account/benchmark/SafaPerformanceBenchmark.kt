package com.safa.account.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.safa.account"
private const val BENCHMARK_HOST_COMPONENT =
    "com.safa.account/com.safa.account.benchmark.BenchmarkHostActivity"
private const val UI_TIMEOUT_MS = 5_000L

@RunWith(AndroidJUnit4::class)
class SafaBaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        BenchmarkFixture.seed(InstrumentationRegistry.getInstrumentation().targetContext)

        // Exercise the real launcher/login shell so startup profile coverage remains
        // representative of production first launch and signed-out startup.
        pressHome()
        startActivityAndWait()

        // Business journeys need deterministic local state but must never use
        // production credentials or services. The benchmark-only host renders
        // the real production screens with a synthetic local operator instead.
        val device = benchmarkDevice()
        pressHome()
        startBenchmarkHost(device)
        exerciseRequiredHighFrequencyJourney(device)
    }
}

@RunWith(AndroidJUnit4::class)
class SafaMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun warmStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    @Test
    fun navigationAndScrollingFrames() {
        BenchmarkFixture.seed(InstrumentationRegistry.getInstrumentation().targetContext)
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            iterations = 6,
            setupBlock = {
                pressHome()
                startBenchmarkHost(benchmarkDevice())
            },
        ) {
            exerciseRequiredHighFrequencyJourney(benchmarkDevice())
        }
    }
}

private fun benchmarkDevice(): UiDevice =
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

private fun startBenchmarkHost(device: UiDevice) {
    device.executeShellCommand("am force-stop $PACKAGE_NAME")
    val startOutput = device.executeShellCommand("am start -W -n $BENCHMARK_HOST_COMPONENT")
    check(startOutput.contains("Status: ok")) {
        "Benchmark host failed to start: $startOutput"
    }
    check(device.wait(Until.hasObject(By.text("Dashboard")), UI_TIMEOUT_MS)) {
        "Benchmark host did not reach the dashboard"
    }
    device.waitForIdle()
}

private fun exerciseRequiredHighFrequencyJourney(device: UiDevice) {
    // Transactions are reached from the production dashboard shortcut before any
    // scrolling can move that shortcut off-screen.
    openRequiredDestination(device, "Transactions")
    scrollRequiredList(device, "Transactions")
    returnToDashboard(device, "Transactions")

    openRequiredDestination(device, "Customers")
    scrollRequiredList(device, "Customers")
    returnToDashboard(device, "Customers")

    openRequiredDestination(device, "Suppliers")
    scrollRequiredList(device, "Suppliers")
    returnToDashboard(device, "Suppliers")

    openRequiredDestination(device, "Wallet")
    scrollRequiredList(device, "Wallet")
    returnToDashboard(device, "Wallet")
}

private fun openRequiredDestination(device: UiDevice, label: String) {
    check(device.wait(Until.hasObject(By.text(label)), UI_TIMEOUT_MS)) {
        "Required benchmark destination is missing: $label"
    }
    val target = checkNotNull(device.findObject(By.text(label))) {
        "Required benchmark destination disappeared: $label"
    }
    target.click()
    device.waitForIdle()
}

private fun scrollRequiredList(device: UiDevice, destination: String) {
    check(device.wait(Until.hasObject(By.scrollable(true)), UI_TIMEOUT_MS)) {
        "Required benchmark list is not scrollable: $destination"
    }
    val scrollable = checkNotNull(device.findObject(By.scrollable(true))) {
        "Required benchmark list disappeared: $destination"
    }
    scrollable.scroll(Direction.DOWN, 0.7f)
    device.waitForIdle()
}

private fun returnToDashboard(device: UiDevice, from: String) {
    device.pressBack()
    check(device.wait(Until.hasObject(By.text("Dashboard")), UI_TIMEOUT_MS)) {
        "Benchmark navigation did not return to Dashboard from $from"
    }
    device.waitForIdle()
}
