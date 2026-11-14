package dev.niltsiar.tvhome.core.model

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

/** app_shelf_sources entries read back into the package and channel they tick. */
val ShelfSourceTest by testSuite {
    test("a watch next entry gives its package and no channel") {
        shelfSourceOf(watchNextSource("com.edde746.plezy")) shouldBe ShelfSource("com.edde746.plezy", null)
    }

    test("a channel entry gives its package and its channel key, which may hold a slash") {
        shelfSourceOf(channelSource("com.liskovsoft.smarttubetv", "history")) shouldBe
            ShelfSource("com.liskovsoft.smarttubetv", "history")
        shelfSourceOf(channelSource("org.jellyfin.androidtv", "server/latest")) shouldBe
            ShelfSource("org.jellyfin.androidtv", "server/latest")
    }
}
