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

@RunWith(AndroidJUnit4::class)
class SafaBaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val packageName = "com.safa.account"

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = packageName,
        includeInStartupProfile = true,
    ) {
        BenchmarkFixture.seed(InstrumentationRegistry.getInstrumentation().targetContext)
        pressHome()
        startActivityAndWait()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.waitForIdle()
        exerciseVisibleHighFrequencyJourney(device)
    }
}

@RunWith(AndroidJUnit4::class)
class SafaMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val packageName = "com.safa.account"

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = packageName,
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
        packageName = packageName,
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
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            iterations = 6,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.waitForIdle()
            exerciseVisibleHighFrequencyJourney(device)
        }
    }
}

private fun exerciseVisibleHighFrequencyJourney(device: UiDevice) {
    val labels = listOf(
        listOf("Dashboard", "ড্যাশবোর্ড"),
        listOf("Customers", "কাস্টমার"),
        listOf("Suppliers", "সাপ্লায়ার", "সাপ্লায়ার"),
        listOf("Transactions", "লেনদেন"),
        listOf("Wallet", "ওয়ালেট", "ওয়ালেট"),
    )

    labels.forEach { alternatives ->
        val target = alternatives.asSequence()
            .mapNotNull { label -> device.findObject(By.text(label)) }
            .firstOrNull()
        if (target != null) {
            target.click()
            device.waitForIdle()
            device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.7f)
            device.waitForIdle()
        }
    }

    // Login is a critical first-run journey and remains valid when the benchmark
    // fixture intentionally starts from a signed-out state.
    device.wait(Until.hasObject(By.res("com.safa.account", "android:id/content")), 2_000)
}
