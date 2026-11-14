package dev.niltsiar.tvhome.service.keys

import android.accessibilityservice.AccessibilityService
import de.infix.testBalloon.framework.core.testSuite
import dev.niltsiar.tvhome.core.model.inputIdOf
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val KeyTargetTest by testSuite {
    val hdmi = "com.tcl.tvinput/.passthroughinput.TvPassThroughService/HW1413743104"

    test("every action word is an action and nothing else is") {
        for (word in listOf("back", "home", "recents", "mute", "sleep", "screensaver", "settings", "wifi", "library", "ignore")) {
            withClue(word) { parseTarget(word) shouldBe KeyTarget.Action(ActionWord.entries.single { it.word == word }) }
        }
        parseTarget("Mute") shouldBe KeyTarget.App("Mute")
    }

    test("targets are told apart by their shape") {
        parseTarget("inputs") shouldBe KeyTarget.Inputs
        parseTarget("guide:com.vt.tv.tvcenter") shouldBe KeyTarget.Guide("com.vt.tv.tvcenter")
        parseTarget("input:$hdmi") shouldBe KeyTarget.Input(hdmi)
        parseTarget("io.github.maxhayim.brightness/.QuickActivity") shouldBe
            KeyTarget.Activity("io.github.maxhayim.brightness/.QuickActivity")
        parseTarget("com.netflix.ninja") shouldBe KeyTarget.App("com.netflix.ninja")
    }

    test("an empty guide or input id is a plain package") {
        parseTarget("guide:") shouldBe KeyTarget.App("guide:")
        parseTarget("input: ") shouldBe KeyTarget.App("input: ")
        inputIdOf("input: ").shouldBeNull()
        inputIdOf("com.netflix.ninja").shouldBeNull()
    }

    test("an input target is not an activity") {
        // Its id has slashes, but it must reach the passthrough branch, not ComponentName.unflattenFromString().
        isActivity("input:$hdmi").shouldBeFalse()
        isActivity("io.github.maxhayim.brightness/.QuickActivity").shouldBeTrue()
        isActivity("com.netflix.ninja").shouldBeFalse()
        isActivity("/.QuickActivity").shouldBeFalse()
    }

    test("global actions and the android 9 floor for sleep") {
        globalAction(ActionWord.Back, 26) shouldBe AccessibilityService.GLOBAL_ACTION_BACK
        globalAction(ActionWord.Home, 26) shouldBe AccessibilityService.GLOBAL_ACTION_HOME
        globalAction(ActionWord.Recents, 26) shouldBe AccessibilityService.GLOBAL_ACTION_RECENTS
        globalAction(ActionWord.Sleep, 28) shouldBe AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
        // No lock-screen action before Android 9: the key is consumed and nothing happens.
        globalAction(ActionWord.Sleep, 27).shouldBeNull()
        globalAction(ActionWord.Mute, 34).shouldBeNull()
    }
}
