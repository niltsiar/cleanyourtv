package dev.niltsiar.tvhome.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

private const val STARTUP_ITERATIONS = 10
private const val SWEEP_ITERATIONS = 5

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

    @Test
    fun coldStartup() =
        rule.measureRepeated(
            packageName = targetPackage,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = Compilation,
            startupMode = StartupMode.COLD,
            iterations = STARTUP_ITERATIONS,
            setupBlock = { pressHome() },
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
                launchHome()
                awaitTileImages()
            },
        ) {
            dpadSweep()
        }
}
