package dev.niltsiar.tvhome

import android.content.ComponentCallbacks2
import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

val MemoryTrimPolicyTest by testSuite {
    test("an ordinary background transition keeps the warm resources") {
        shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, homeStarted = false).shouldBeFalse()
        shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, homeStarted = true).shouldBeFalse()
    }

    test("background pressure releases the resources only when home is stopped") {
        shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, homeStarted = false).shouldBeTrue()
        shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, homeStarted = false).shouldBeTrue()
        shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, homeStarted = true).shouldBeFalse()
        shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, homeStarted = true).shouldBeFalse()
    }

    test("low memory signals release the resources when home is stopped") {
        shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, homeStarted = false).shouldBeTrue()
        shouldReleaseOnTrim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, homeStarted = false).shouldBeTrue()
    }
}
