package dev.niltsiar.tvhome.core.designsystem.golden

import androidx.compose.ui.graphics.ImageBitmap
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max

/** Set by the test task when Gradle runs with `-Pgolden.record`. */
private const val RECORD_PROPERTY = "golden.record"

/** The Gradle project under test, set by the test task, for the record command in a failure message. */
private const val MODULE_PROPERTY = "golden.module"

/** The most any channel of any pixel may differ from the golden, in steps of 1/255. */
private const val TOLERANCE = 1

/**
 * Compares this capture with the golden `src/test/resources/golden/<suite>/<scene>.png` of the module under test, pixel
 * by pixel: it matches when no channel differs by more than 1. A missing golden fails, unless Gradle runs with
 * `-Pgolden.record`, which writes a missing or differing golden and passes. On a mismatch the capture and a diff image
 * go to `build/golden-failures/<suite>/<scene>-actual.png` and `<scene>-diff.png`.
 */
public fun ImageBitmap.assertMatchesGolden(
    suite: String,
    scene: String,
) {
    // Gradle runs a module's unit tests in the module's directory.
    val module = File("").absoluteFile
    check(File(module, "src/test").isDirectory) { "Golden tests must run with the module directory as the working directory, not $module" }
    val golden = File(module, "src/test/resources/golden/$suite/$scene.png")
    val actual = IntArray(width * height).also { readPixels(it) }
    val expectedImage = if (golden.isFile) ImageIO.read(golden) else null
    val expected =
        expectedImage
            ?.takeIf { it.width == width && it.height == height }
            ?.getRGB(0, 0, width, height, null, 0, width)

    var differing = 0
    var maxDelta = 0
    val diff = IntArray(actual.size)
    if (expected != null) {
        for (i in actual.indices) {
            val delta = channelDelta(expected[i], actual[i])
            maxDelta = max(maxDelta, delta)
            if (delta > TOLERANCE) differing++
            diff[i] = if (delta > TOLERANCE) 0xFFFF0000.toInt() else faded(expected[i])
        }
        if (differing == 0) return
    }
    if (System.getProperty(RECORD_PROPERTY).toBoolean()) {
        writePng(golden, width, height, actual)
        return
    }

    val record = "Record it with ${recordCommand()} and check the image."
    if (expectedImage == null) throw AssertionError("No golden $suite/$scene.png. $record")
    val failures = File(module, "build/golden-failures/$suite")
    writePng(File(failures, "$scene-actual.png"), width, height, actual)
    if (expected == null) {
        throw AssertionError(
            "Golden $suite/$scene.png is ${expectedImage.width}x${expectedImage.height} px, the capture is ${width}x$height px. " +
                "See ${failures.relativeTo(module)}. If the change is intended: $record",
        )
    }
    writePng(File(failures, "$scene-diff.png"), width, height, diff)
    throw AssertionError(
        "Golden $suite/$scene.png: $differing pixels differ by more than $TOLERANCE, at most $maxDelta. " +
            "See ${failures.relativeTo(module)}. If the change is intended: $record",
    )
}

private fun recordCommand(): String {
    val module = System.getProperty(MODULE_PROPERTY) ?: ":<module>"
    return "./gradlew $module:testDebugUnitTest -P$RECORD_PROPERTY"
}

/** The largest difference between two ARGB pixels over their four channels. */
private fun channelDelta(
    a: Int,
    b: Int,
): Int {
    var delta = 0
    for (shift in 0..24 step 8) delta = max(delta, abs((a ushr shift and 0xFF) - (b ushr shift and 0xFF)))
    return delta
}

/** A matching pixel in the diff image: the golden's grey level at a quarter, so the red differing pixels stand out. */
private fun faded(argb: Int): Int {
    val grey = ((argb ushr 16 and 0xFF) + (argb ushr 8 and 0xFF) + (argb and 0xFF)) / 12
    return (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
}

/** Writes [pixels] as a PNG, without an alpha channel when every pixel is opaque. */
private fun writePng(
    file: File,
    width: Int,
    height: Int,
    pixels: IntArray,
) {
    val opaque = pixels.all { it ushr 24 == 0xFF }
    val image = BufferedImage(width, height, if (opaque) BufferedImage.TYPE_INT_RGB else BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, width, height, pixels, 0, width)
    file.parentFile.mkdirs()
    check(ImageIO.write(image, "png", file)) { "No PNG writer for $file" }
}
