package dev.niltsiar.tvhome.data.apps

import dev.niltsiar.tvhome.core.config.TvConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AppListRulesTest {
    private val config = TvConfig(dockApps = listOf("b", "a"), hiddenApps = listOf("hidden"))

    private fun candidate(
        pkg: String,
        label: String = pkg.uppercase(),
        activity: String = ".Main",
        exported: Boolean = true,
    ) = Candidate("$pkg/$activity", pkg, label, exported)

    private fun arrange(
        vararg candidates: Candidate,
        config: TvConfig = this.config,
        order: Order? = null,
        tv: Set<String> = candidates.map { it.packageName }.toSet(),
        choices: LibraryChoices = LibraryChoices(added = config.libraryApps.toSet()),
    ) = arrange(candidates.toList(), tv, config, order, ownPackage = "own", choices = choices)

    private fun Arrangement.keys() = tiles.map { it.candidate.packageName }

    @Test
    fun dock_apps_come_first_in_dock_apps_order_then_the_rest_by_name() {
        val result = arrange(candidate("z"), candidate("a"), candidate("c", "banana"), candidate("b"), candidate("d", "Apple"))

        assertEquals(listOf("b", "a", "d", "c", "z"), result.keys())
        assertEquals(2, result.docked)
    }

    @Test
    fun the_saved_order_goes_before_dock_apps_and_names() {
        val order = Order(Order.dockKey(config.dockApps), listOf("z/.Main", "c/.Main"))

        val result = arrange(candidate("c"), candidate("a"), candidate("z"), candidate("b"), order = order)

        assertEquals(listOf("z", "c", "b", "a"), result.keys())
    }

    @Test
    fun a_saved_order_made_under_other_dock_apps_is_ignored() {
        val order = Order(Order.dockKey(listOf("x")), listOf("z/.Main", "c/.Main"))

        val result = arrange(candidate("c"), candidate("a"), candidate("z"), candidate("b"), order = order)

        assertEquals(listOf("b", "a", "c", "z"), result.keys())
    }

    @Test
    fun a_saved_dock_size_decides_which_leading_tiles_are_docked() {
        val order = Order(Order.dockKey(config.dockApps), listOf("z/.Main", "b/.Main", "a/.Main"), docked = 1)

        val result = arrange(candidate("a"), candidate("b"), candidate("z"), order = order)

        assertEquals(listOf("z", "b", "a"), result.keys())
        assertEquals(1, result.docked)
    }

    @Test
    fun hidden_unexported_and_own_activities_are_not_shown() {
        val result =
            arrange(candidate("hidden"), candidate("closed", exported = false), candidate("own"), candidate("shown"))

        assertEquals(listOf("shown"), result.keys())
    }

    @Test
    fun a_phone_app_shows_only_when_library_apps_adds_its_package() {
        val candidates = arrayOf(candidate("phone"), candidate("tv"))
        val tv = setOf("tv")

        assertEquals(listOf("tv"), arrange(*candidates, tv = tv).keys())
        assertEquals(listOf("phone", "tv"), arrange(*candidates, tv = tv, config = config.copy(libraryApps = listOf("phone"))).keys())
    }

    @Test
    fun a_library_app_shows_even_when_hidden_apps_lists_it() {
        val result = arrange(candidate("hidden"), config = config.copy(libraryApps = listOf("hidden")), tv = setOf("hidden"))

        assertEquals(listOf("hidden"), result.keys())
    }

    @Test
    fun a_package_the_owner_added_in_the_library_shows_like_one_from_library_apps() {
        val candidates = arrayOf(candidate("phone"), candidate("hidden"), candidate("tv"))
        val tv = setOf("hidden", "tv")

        val result = arrange(*candidates, tv = tv, choices = LibraryChoices(added = setOf("phone", "hidden")))

        assertEquals(setOf("phone", "hidden", "tv"), result.keys().toSet())
    }

    @Test
    fun a_package_the_owner_removed_never_shows_whatever_else_adds_it() {
        val candidates = arrayOf(candidate("tv"), candidate("docked", "Docked"), candidate("seeded"), candidate("kept"))
        val choices = LibraryChoices(added = setOf("seeded", "docked"), removed = setOf("tv", "docked", "seeded"))

        val result = arrange(*candidates, config = config.copy(libraryApps = listOf("seeded")), choices = choices)

        assertEquals(listOf("kept"), result.keys())
    }

    @Test
    fun app_labels_rename_a_tile() {
        val result = arrange(candidate("c", "Cee"), config = config.copy(appLabels = mapOf("c" to "Channel")))

        assertEquals("Channel", result.tiles.single().label)
    }

    @Test
    fun two_activities_with_one_package_and_name_are_one_tile_but_two_names_are_two() {
        val result =
            arrange(
                candidate("c", "Clock", ".First"),
                candidate("c", "Clock", ".Second"),
                candidate("c", "Timers", ".Third"),
            )

        assertEquals(listOf("c/.First", "c/.Third"), result.tiles.map { it.candidate.key })
    }

    @Test
    fun a_label_cannot_redraw_itself_with_control_characters() {
        assertEquals("evil name", cleanLabel("‮evil name\n", "pkg"))
    }

    @Test
    fun a_label_is_cut_at_40_characters_without_splitting_a_surrogate_pair() {
        val label = "x".repeat(39) + "😀"

        assertEquals("x".repeat(39), cleanLabel(label, "pkg"))
    }

    @Test
    fun a_label_with_nothing_visible_falls_back_to_the_package() {
        assertEquals("pkg", cleanLabel(" ​\n", "pkg"))
    }
}
