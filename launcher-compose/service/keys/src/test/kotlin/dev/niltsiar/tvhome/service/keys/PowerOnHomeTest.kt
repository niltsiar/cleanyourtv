package dev.niltsiar.tvhome.service.keys

import android.view.KeyEvent
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

val PowerOnHomeTest by testSuite {
    /** The unit on a clock and a timer that move together. */
    class Rig(
        plan: PowerOnPlan,
        bootedMs: Long,
    ) {
        val timer = FakeTimer()
        val homes = mutableListOf<Long>()
        val power = PowerOnHome(plan, { bootedMs + timer.now }, timer) { homes += timer.now }
    }

    val soon = 45_000L
    val tv = "com.mediatek.wwtv.tvcenter"

    val picking = TvConfig(openOnStartChoice = true)

    test("a tap is a short press and a hold past the timeout is not") {
        isShortPress(1_000, 1_120, 400).shouldBeTrue()
        isShortPress(1_000, 1_400, 400).shouldBeFalse()
        isShortPress(1_000, 3_000, 400).shouldBeFalse()
    }

    test("the first bind of a new boot probes") {
        firstBindAfterBoot(12, 11, soon).shouldBeTrue()
        firstBindAfterBoot(1, -1, soon).shouldBeTrue()
    }

    test("a rebind in the same boot a late bind or no boot count does not") {
        firstBindAfterBoot(12, 12, soon).shouldBeFalse()
        firstBindAfterBoot(12, 11, BOOT_WINDOW_MS).shouldBeFalse()
        firstBindAfterBoot(-1, -1, soon).shouldBeFalse()
    }

    test("live tv before any key goes home") {
        powerOnRedirect(tv, tv, soon, BOOT_WINDOW_MS).shouldBeTrue()
    }

    test("other apps and inputs are left alone") {
        powerOnRedirect(tv, "com.netflix.ninja", soon, BOOT_WINDOW_MS).shouldBeFalse()
        powerOnRedirect(tv, null, soon, BOOT_WINDOW_MS).shouldBeFalse()
        powerOnRedirect("", tv, soon, BOOT_WINDOW_MS).shouldBeFalse()
    }

    test("after a key or the window live tv stays") {
        powerOnRedirect(tv, tv, soon, 0).shouldBeFalse()
        powerOnRedirect(tv, tv, BOOT_WINDOW_MS, BOOT_WINDOW_MS).shouldBeFalse()
    }

    test("still armed until the window ends") {
        stillArmed(soon, soon + SCREEN_ON_WINDOW_MS).shouldBeTrue()
        stillArmed(soon, 0).shouldBeFalse()
    }

    test("boot to live tv switches the power on keys off") {
        val both = TvConfig(powerOnHome = true, powerOnHomeAlways = true)
        powerOnPlan(both, null) shouldBe PowerOnPlan(tv, always = true)
        powerOnPlan(both.copy(bootToLiveTv = true), null) shouldBe PowerOnPlan("", always = false)
        powerOnPlan(TvConfig(), null).enabled.shouldBeFalse()
        powerOnPlan(both.copy(liveTvPackage = null, powerOnHomeAlways = false), null).enabled.shouldBeFalse()
    }

    test("live tv picked as the open on start app is not sent home") {
        val config = picking.copy(powerOnHome = true)
        powerOnPlan(config, tv) shouldBe PowerOnPlan("", always = false)
    }

    test("another app picked leaves the live tv redirect") {
        val config = picking.copy(powerOnHome = true)
        powerOnPlan(config, "com.netflix.ninja") shouldBe PowerOnPlan(tv, always = false)
    }

    test("any pick turns power on home always off and no pick leaves it on") {
        val config = picking.copy(powerOnHomeAlways = true)
        powerOnPlan(config, "com.netflix.ninja") shouldBe PowerOnPlan("", always = false)
        powerOnPlan(config, tv) shouldBe PowerOnPlan("", always = false)
        powerOnPlan(config, null) shouldBe PowerOnPlan("", always = true)
    }

    test("a stored pick is ignored while open on start choice is off") {
        val config = TvConfig(openOnStartChoice = false, powerOnHome = true, powerOnHomeAlways = true)
        powerOnPlan(config, tv) shouldBe PowerOnPlan(tv, always = true)
        powerOnPlan(config, "com.netflix.ninja") shouldBe PowerOnPlan(tv, always = true)
    }

    test("always opens home 800 ms after the screen comes on") {
        val rig = Rig(PowerOnPlan("", always = true), bootedMs = 10 * 60_000L)
        rig.power.armAtBind()
        rig.timer.pending shouldBe 0 // bound long after boot: nothing to do until the screen turns on

        rig.power.onScreenOn()
        rig.timer.advance(POWER_ON_ALWAYS_DELAY_MS - 1)
        rig.homes shouldBe emptyList<Long>()
        rig.timer.advance(1)
        rig.homes shouldBe listOf(POWER_ON_ALWAYS_DELAY_MS)
    }

    test("a key pressed before the delay keeps home from opening") {
        val rig = Rig(PowerOnPlan("", always = true), bootedMs = 10 * 60_000L)
        rig.power.onScreenOn()
        rig.timer.advance(300)
        rig.power.onKeyDown(KeyEvent.KEYCODE_DPAD_CENTER)
        rig.timer.advance(POWER_ON_ALWAYS_DELAY_MS)
        rig.homes shouldBe emptyList<Long>()
    }

    test("the power and wake keys do not count as a key") {
        val rig = Rig(PowerOnPlan("", always = true), bootedMs = 10 * 60_000L)
        rig.power.onScreenOn()
        rig.power.onKeyDown(KeyEvent.KEYCODE_POWER)
        rig.power.onKeyDown(KeyEvent.KEYCODE_WAKEUP)
        rig.timer.advance(POWER_ON_ALWAYS_DELAY_MS)
        rig.homes.size shouldBe 1
    }

    test("a bind during the boot opens home too") {
        val rig = Rig(PowerOnPlan("", always = true), bootedMs = 30_000L)
        rig.power.armAtBind()
        rig.timer.advance(POWER_ON_ALWAYS_DELAY_MS)
        rig.homes.size shouldBe 1
    }

    test("without always the screen coming on opens nothing by itself") {
        val rig = Rig(PowerOnPlan(tv, always = false), bootedMs = 10 * 60_000L)
        rig.power.onScreenOn()
        rig.timer.advance(POWER_ON_ALWAYS_DELAY_MS * 10)
        rig.homes shouldBe emptyList<Long>()
    }

    test("live tv is sent home for 60 seconds after the screen comes on until a key") {
        val rig = Rig(PowerOnPlan(tv, always = false), bootedMs = 10 * 60_000L)
        rig.power.onScreenOn()
        rig.timer.advance(SCREEN_ON_WINDOW_MS - 1)
        rig.power.redirects(tv).shouldBeTrue()
        rig.power.redirects("com.netflix.ninja").shouldBeFalse()
        rig.power.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN)
        rig.power.redirects(tv).shouldBeFalse()

        rig.power.onScreenOn()
        rig.timer.advance(SCREEN_ON_WINDOW_MS)
        rig.power.redirects(tv).shouldBeFalse()
    }
}
