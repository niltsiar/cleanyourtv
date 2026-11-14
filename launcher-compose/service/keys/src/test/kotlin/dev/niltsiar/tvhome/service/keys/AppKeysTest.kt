package dev.niltsiar.tvhome.service.keys

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

val AppKeysTest by testSuite {
    // Key names resolved by a stand-in: android.view.KeyEvent is not backed by code in JVM tests.
    fun keyCode(name: String): Int =
        when (name) {
            "KEYCODE_BUTTON_3" -> 190
            "KEYCODE_BUTTON_8" -> 195
            else -> 0
        }

    fun parse(vararg entries: String) = parseAppKeys(entries.asList(), ::keyCode)

    fun keysOf(vararg entries: String) = AppKeys(entries.asList(), ::keyCode)

    test("key names work like numbers") {
        val keys = parse("KEYCODE_BUTTON_3:com.google.android.youtube.tv", "KEYCODE_NOT_A_KEY:a.b")
        keys shouldBe mapOf(190 to "com.google.android.youtube.tv")
    }

    test("a keycode and a package with spaces around them") {
        val keys = parse("190:com.google.android.youtube.tv", " 193 : com.netflix.ninja ")
        keys shouldBe mapOf(190 to "com.google.android.youtube.tv", 193 to "com.netflix.ninja")
    }

    test("bad entries are skipped") {
        parse("com.google.android.youtube.tv", "youtube:com.google.android.youtube.tv", "190:", "").shouldBeEmpty()
        parseAppKeys(listOf(null)).shouldBeEmpty()
    }

    test("the last entry for a keycode wins") {
        parse("190:a.b", "190:c.d")[190] shouldBe "c.d"
    }

    test("scancode entries are read apart from keycode entries") {
        val entries = listOf("scan:490:com.wbd.hbomax", " scan : 491 : a.b ", "scan:x:a.b", "scan:12:", "190:c.d", "scan:491:e.f")
        parseAppScancodes(entries) shouldBe mapOf(490 to "com.wbd.hbomax", 491 to "e.f")
        parseAppKeys(entries, ::keyCode) shouldBe mapOf(190 to "c.d")
    }

    test("hold entries get their own maps") {
        val keys =
            keysOf(
                "hold:KEYCODE_BUTTON_8:org.xbmc.kodi",
                " hold:190:a.b ",
                "hold:KEYCODE_NOT_A_KEY:x.y",
                "hold:195:",
                "190:c.d",
                "hold:scan:490:d.e",
                "hold:scan:491:",
                "hold:",
            )
        keys.holdKeycodes shouldBe mapOf(195 to "org.xbmc.kodi", 190 to "a.b")
        keys.holdScancodes shouldBe mapOf(490 to "d.e")
        keys.keycodes shouldBe mapOf(190 to "c.d")
        keys.scancodes.shouldBeEmpty()
    }

    test("front entries apply while the buttons own app is in front") {
        val keys =
            keysOf(
                "172:com.vt.tv.tvcenter",
                "front:172:guide:com.vt.tv.tvcenter",
                "scan:490:com.wbd.hbomax",
                "front:scan:490:input:a/.B/HW1",
                "hold:172:a.b",
            )
        keys.watchesFront.shouldBeTrue()
        keys.keyTap(172, "com.vt.tv.tvcenter") shouldBe "guide:com.vt.tv.tvcenter"
        keys.keyTap(172, "com.gathin.tvhome") shouldBe "com.vt.tv.tvcenter"
        keys.keyTap(172, "") shouldBe "com.vt.tv.tvcenter"
        keys.scanTap(490, "com.wbd.hbomax") shouldBe "input:a/.B/HW1"
        keys.scanTap(490, "other") shouldBe "com.wbd.hbomax"
        keys.holdKeycodes shouldBe mapOf(172 to "a.b")
        keysOf("172:a.b").watchesFront.shouldBeFalse()
    }

    test("in front matches the targets package only") {
        inFront("com.vt.tv.tvcenter", "com.vt.tv.tvcenter").shouldBeTrue()
        inFront("io.github.maxhayim.brightness/.QuickActivity", "io.github.maxhayim.brightness").shouldBeTrue()
        inFront("com.vt.tv.tvcenter", "com.gathin.tvhome").shouldBeFalse()
        inFront("com.vt.tv.tvcenter", "").shouldBeFalse()
        inFront(null, "com.vt.tv.tvcenter").shouldBeFalse()
        inFront("input:com.vt.source.external/.HdmiInputService/HW2", "com.vt.source.external").shouldBeFalse()
    }

    test("hold time picks the hold target") {
        tapOrHold("app", "hold.app", HOLD_MS) shouldBe "hold.app"
        tapOrHold("app", "hold.app", 5000) shouldBe "hold.app"
        tapOrHold("app", "hold.app", HOLD_MS - 1) shouldBe "app"
        tapOrHold("app", null, 5000) shouldBe "app"
        tapOrHold(null, "hold.app", 0).shouldBeNull()
    }
}
