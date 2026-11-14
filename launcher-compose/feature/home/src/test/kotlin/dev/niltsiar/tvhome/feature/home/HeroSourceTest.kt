package dev.niltsiar.tvhome.feature.home

import de.infix.testBalloon.framework.core.testSuite
import io.kotest.matchers.shouldBe

val HeroSourceTest by testSuite {
    test("with dock title on the focused tile is the line when nothing else claims the band") {
        heroSource(dockTitle = true, hint = null, pillFocused = false, control = null) shouldBe HeroSource.Tile
    }

    test("with dock title off the focused tile gets no line") {
        heroSource(dockTitle = false, hint = null, pillFocused = false, control = null) shouldBe HeroSource.Quiet
    }

    test("a reorder hint a library pill and a bar control keep their line with dock title off") {
        heroSource(dockTitle = false, hint = ReorderHint.Reorder, pillFocused = false, control = null) shouldBe
            HeroSource.Mode(ReorderHint.Reorder)
        heroSource(dockTitle = false, hint = null, pillFocused = true, control = null) shouldBe HeroSource.Library
        heroSource(dockTitle = false, hint = null, pillFocused = false, control = BarControl.Network) shouldBe
            HeroSource.Bar(BarControl.Network)
    }

    test("a mode beats the pill and the pill beats a bar control") {
        heroSource(dockTitle = true, hint = ReorderHint.Reorder, pillFocused = true, control = BarControl.Network) shouldBe
            HeroSource.Mode(ReorderHint.Reorder)
        heroSource(dockTitle = true, hint = null, pillFocused = true, control = BarControl.Network) shouldBe HeroSource.Library
    }
}
