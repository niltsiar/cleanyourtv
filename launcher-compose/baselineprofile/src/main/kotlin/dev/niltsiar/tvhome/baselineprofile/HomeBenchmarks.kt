package dev.niltsiar.tvhome.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

private const val STARTUP_ITERATIONS = 10
private const val SWEEP_ITERATIONS = 5

/** Each round trip picks the other Style and then the first one again, so every iteration ends in the look it began in. */
private const val LOOK_ROUND_TRIPS = 3

/**
 * Baseline profile collection needs API 33+ or root; without a shipped profile, ART compiles a speed-profile from
 * 3 warmup runs, as in android/performance-samples MacrobenchmarkSample.
 */
private val Compilation = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable, warmupIterations = 3)

/** The gate's measurements; `scripts/spike-gate.sh` runs this class against the connected TV. */
@OptIn(ExperimentalMetricApi::class)
class HomeBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** JUnit builds one instance per test, and each test's reset deletes the theme once, before its first setup block. */
    private var themePushed = false

    private fun MacrobenchmarkScope.pushThemeOnce() {
        if (themePushed) return
        themePushed = true
        pushTheme()
    }

    @Test
    fun coldStartup() =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = Compilation,
            startupMode = StartupMode.COLD,
            iterations = STARTUP_ITERATIONS,
            setupBlock = {
                pushThemeOnce()
                pressHome()
            },
        ) {
            launchHome()
        }

    @Test
    fun dpadSweep() =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
            compilationMode = Compilation,
            startupMode = StartupMode.WARM,
            iterations = SWEEP_ITERATIONS,
            setupBlock = {
                pushThemeOnce()
                launchHome()
                awaitTileImages()
            },
        ) {
            dpadSweep()
        }

    @Test
    fun libraryHoldDown() =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
            compilationMode = Compilation,
            startupMode = StartupMode.WARM,
            iterations = SWEEP_ITERATIONS,
            setupBlock = {
                pushThemeOnce()
                launchHome()
                awaitTileImages()
            },
        ) {
            openLibrary()
            libraryHoldDown()
        }

    /**
     * Each pick stores the look as a Settings choice, which outlives a theme push, so the test ends on Follow theme: the bench
     * copy's look is its theme's again, also when a run fails part way.
     */
    @Test
    fun lookSwitch() {
        try {
            rule.measureRepeated(
                packageName = targetPackage,
                metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Last)),
                compilationMode = Compilation,
                startupMode = StartupMode.WARM,
                iterations = SWEEP_ITERATIONS,
                setupBlock = {
                    pushThemeOnce()
                    launchHome()
                    openLookRow()
                },
            ) {
                repeat(LOOK_ROUND_TRIPS) {
                    flipLook()
                    flipLook()
                }
            }
        } finally {
            MacrobenchmarkScope(targetPackage, launchWithClearTask = true).run {
                launchHome()
                followTheme()
            }
        }
    }
}
