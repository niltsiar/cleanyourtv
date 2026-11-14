package dev.niltsiar.tvhome.data.shelf

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.media.tv.TvContract
import android.media.tv.TvContract.Channels
import android.media.tv.TvContract.PreviewPrograms
import android.media.tv.TvContract.WatchNextPrograms
import android.net.Uri
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import dev.niltsiar.tvhome.core.model.PublishedChannel
import dev.niltsiar.tvhome.core.model.ShelfChannel
import dev.niltsiar.tvhome.core.model.ShelfItem
import dev.niltsiar.tvhome.core.model.ShelfKind
import dev.niltsiar.tvhome.core.model.ShelfPublisher
import dev.niltsiar.tvhome.core.model.channelSource
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

private const val NOW = 1_800_000_000_000L
private const val DAY = 24L * 60 * 60 * 1000
private const val PLEZY = "com.edde746.plezy"
private const val DAZN = "com.dazn"
private const val SMARTTUBE = "org.smarttube.stable"
private const val JELLYFIN = "org.jellyfin.androidtv"

private fun watchNext(
    pkg: String,
    title: String,
    vararg columns: Pair<String, Any?>,
): Map<String, Any?> = mapOf(WatchNextPrograms.COLUMN_PACKAGE_NAME to pkg, WatchNextPrograms.COLUMN_TITLE to title) + columns

/** Shaped like Plezy's Watch Next rows: an episode to continue, a local poster Plezy grants to Home, a plezy:// intent. */
private fun plezy(
    title: String,
    engaged: Long = NOW - DAY,
    vararg columns: Pair<String, Any?>,
): Map<String, Any?> =
    watchNext(
        PLEZY,
        title,
        WatchNextPrograms.COLUMN_TYPE to WatchNextPrograms.TYPE_TV_EPISODE,
        WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE to WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE,
        WatchNextPrograms.COLUMN_LIVE to 0,
        WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS to 600_000,
        WatchNextPrograms.COLUMN_DURATION_MILLIS to 3_000_000,
        WatchNextPrograms.COLUMN_POSTER_ART_URI to "content://$PLEZY.systemshelf/art/$title",
        WatchNextPrograms.COLUMN_INTENT_URI to "plezy://play?content_id=plezy_srv_$title",
        WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS to engaged,
        *columns,
    )

/** Shaped like DAZN's rows: live events. */
private fun daznEvent(title: String): Map<String, Any?> =
    watchNext(
        DAZN,
        title,
        WatchNextPrograms.COLUMN_TYPE to WatchNextPrograms.TYPE_EVENT,
        WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE to WatchNextPrograms.WATCH_NEXT_TYPE_NEXT,
        WatchNextPrograms.COLUMN_LIVE to 1,
        WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS to NOW - DAY,
    )

/** A row of the same series ("Show") to [watchNextType], [type] and [live], engaged a day ago. */
private fun episode(
    title: String,
    watchNextType: Int = WatchNextPrograms.WATCH_NEXT_TYPE_NEXT,
    type: Int = WatchNextPrograms.TYPE_TV_EPISODE,
    live: Int = 0,
): Map<String, Any?> =
    watchNext(
        PLEZY,
        title,
        WatchNextPrograms.COLUMN_TYPE to type,
        WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE to watchNextType,
        WatchNextPrograms.COLUMN_LIVE to live,
        WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS to NOW - DAY,
    )

private fun channel(
    id: Long,
    pkg: String,
    name: String,
    internalId: String?,
): Map<String, Any?> =
    mapOf(
        Channels._ID to id,
        Channels.COLUMN_PACKAGE_NAME to pkg,
        Channels.COLUMN_TYPE to Channels.TYPE_PREVIEW,
        Channels.COLUMN_DISPLAY_NAME to name,
        Channels.COLUMN_INTERNAL_PROVIDER_ID to internalId,
        Channels.COLUMN_BROWSABLE to 0,
    )

/** Shaped like SmartTube's channel programs: a video with an https poster and an intent to SmartTube's own activity. */
private fun video(
    channelId: Long,
    title: String,
    weight: Int,
    pkg: String = SMARTTUBE,
): Map<String, Any?> =
    mapOf(
        PreviewPrograms.COLUMN_PACKAGE_NAME to pkg,
        PreviewPrograms.COLUMN_CHANNEL_ID to channelId,
        PreviewPrograms.COLUMN_TITLE to title,
        PreviewPrograms.COLUMN_TYPE to PreviewPrograms.TYPE_MOVIE,
        PreviewPrograms.COLUMN_WEIGHT to weight,
        PreviewPrograms.COLUMN_POSTER_ART_URI to "https://i.ytimg.com/vi/$title/hqdefault.jpg",
        PreviewPrograms.COLUMN_INTENT_URI to "intent://www.youtube.com/watch?v=$title#Intent;scheme=https;component=$pkg/.Main;end",
    )

/** What the reader makes of the app's rows, against a fake TV provider. */
val ShelfReaderTest by testSuite {
    robolectricTestSuite<ShelfReaderTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class ShelfReaderTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app = RuntimeEnvironment.getApplication()
                val provider: FakeTvProvider = Robolectric.setupContentProvider(FakeTvProvider::class.java, TvContract.AUTHORITY)
                val config = MutableStateFlow(TvConfig(appShelf = true))
                val reader = ShelfReader(app, config, Dispatchers.Unconfined) { NOW }

                init {
                    shadowOf(app).grantPermissions(READ_TV_LISTINGS)
                }

                fun tick(vararg sources: String) {
                    config.value = TvConfig(appShelf = true, appShelfSources = sources.toList())
                }

                fun items(pkg: String) = runBlocking { reader.items(pkg) }

                fun titles(pkg: String) = items(pkg).map { it.title }

                fun publishers() = runBlocking { reader.publishers() }

                fun install(
                    pkg: String,
                    label: String,
                ) {
                    val info =
                        ApplicationInfo().apply {
                            packageName = pkg
                            nonLocalizedLabel = label
                        }
                    shadowOf(app.packageManager).installPackage(
                        PackageInfo().apply {
                            packageName = pkg
                            applicationInfo = info
                        },
                    )
                }

                /** SmartTube's three channels, History with three videos, Recommended with two and Subscriptions empty. */
                fun smartTube() {
                    provider.channels += channel(1, SMARTTUBE, "Historial", "history")
                    provider.channels += channel(2, SMARTTUBE, "Recomendados", "recommended")
                    provider.channels += channel(3, SMARTTUBE, "Suscripciones", "subscriptions")
                    provider.programs += video(1, "older", weight = 1)
                    provider.programs += video(1, "newest", weight = 3)
                    provider.programs += video(1, "middle", weight = 2)
                    provider.programs += video(2, "suggested", weight = 1)
                    provider.programs += video(2, "another", weight = 2)
                }
            }
        } asContextForEach {
            test("with app_shelf off nothing is read") {
                provider.watchNext += plezy("Severance")
                smartTube()
                config.value = TvConfig(appShelf = false, appShelfSources = listOf(PLEZY, channelSource(SMARTTUBE, "history")))

                items(PLEZY).shouldBeEmpty()
                items(SMARTTUBE).shouldBeEmpty()
                publishers().shouldBeEmpty()

                provider.queries.shouldBeEmpty()
            }

            test("without read tv listings nothing is read") {
                provider.watchNext += plezy("Severance")
                shadowOf(app).denyPermissions(READ_TV_LISTINGS)
                tick(PLEZY)

                items(PLEZY).shouldBeEmpty()
                publishers().shouldBeEmpty()

                provider.queries.shouldBeEmpty()
            }

            test("a ticked app's watch next row becomes an item with every field") {
                provider.watchNext +=
                    plezy(
                        "Severance",
                        NOW - 2 * DAY,
                        WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER to "2",
                        WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER to "4",
                        WatchNextPrograms.COLUMN_EPISODE_TITLE to "Woe's Hollow",
                        WatchNextPrograms.COLUMN_SHORT_DESCRIPTION to "The team goes outdoors.",
                    )
                tick(PLEZY)

                items(PLEZY) shouldBe
                    listOf(
                        ShelfItem(
                            packageName = PLEZY,
                            title = "Severance",
                            kind = ShelfKind.Continue,
                            seasonNumber = "2",
                            episodeNumber = "4",
                            episodeTitle = "Woe's Hollow",
                            description = "The team goes outdoors.",
                            positionMs = 600_000,
                            durationMs = 3_000_000,
                            posterUri = "content://$PLEZY.systemshelf/art/Severance",
                            intentUri = "plezy://play?content_id=plezy_srv_Severance",
                            lastEngagementMs = NOW - 2 * DAY,
                        ),
                    )
            }

            test("an item without poster art shows its thumbnail") {
                provider.watchNext +=
                    watchNext(
                        JELLYFIN,
                        "Dune",
                        WatchNextPrograms.COLUMN_TYPE to WatchNextPrograms.TYPE_MOVIE,
                        WatchNextPrograms.COLUMN_THUMBNAIL_URI to "content://$JELLYFIN/thumb/dune",
                    )
                tick(JELLYFIN)

                items(JELLYFIN).map { it.posterUri } shouldBe listOf("content://$JELLYFIN/thumb/dune")
            }

            test("only the ticked sources of the asked app are read, by package") {
                provider.watchNext += plezy("Severance")
                provider.watchNext += daznEvent("Final")
                provider.watchNext += watchNext(JELLYFIN, "Dune", WatchNextPrograms.COLUMN_TYPE to WatchNextPrograms.TYPE_MOVIE)
                smartTube()
                tick(PLEZY)

                items(DAZN).shouldBeEmpty()
                items(SMARTTUBE).shouldBeEmpty()
                provider.queries.shouldBeEmpty()

                titles(PLEZY) shouldBe listOf("Severance")
                provider.queries shouldBe listOf(Uri.parse("content://android.media.tv/watch_next_program?package=$PLEZY"))
            }

            test("a ticked channel gives its programs by weight with the channel, and nothing else of the app is read") {
                smartTube()
                provider.watchNext += watchNext(SMARTTUBE, "not ticked")
                tick(channelSource(SMARTTUBE, "history"))

                val items = items(SMARTTUBE)

                items.map { it.title } shouldBe listOf("newest", "middle", "older")
                items.map { it.kind }.distinct() shouldBe listOf(ShelfKind.Preview)
                items.map { it.channel }.distinct() shouldBe listOf(ShelfChannel("history", "Historial"))
                items.first().posterUri shouldBe "https://i.ytimg.com/vi/newest/hqdefault.jpg"
                provider.queries shouldBe
                    listOf(
                        Uri.parse("content://android.media.tv/channel?preview=true&package=$SMARTTUBE"),
                        Uri.parse("content://android.media.tv/preview_program?channel=1&package=$SMARTTUBE"),
                    )
            }

            test("a channel without an internal provider id is ticked by its row id") {
                provider.channels += channel(7, JELLYFIN, "Latest", null)
                provider.programs += video(7, "Arrival", weight = 0, pkg = JELLYFIN)
                tick(channelSource(JELLYFIN, "7"))

                items(JELLYFIN).map { it.channel } shouldBe listOf(ShelfChannel("7", "Latest"))
            }

            test("live programs are dropped") {
                provider.watchNext += episode("live", type = WatchNextPrograms.TYPE_CLIP, live = 1)
                provider.watchNext += episode("recorded", type = WatchNextPrograms.TYPE_CLIP, live = 0)
                tick(PLEZY)

                titles(PLEZY) shouldBe listOf("recorded")
            }

            test("channel and event programs are dropped") {
                provider.watchNext += episode("channel", type = WatchNextPrograms.TYPE_CHANNEL)
                provider.watchNext += episode("event", type = WatchNextPrograms.TYPE_EVENT)
                provider.watchNext += episode("movie", type = WatchNextPrograms.TYPE_MOVIE)
                tick(PLEZY)

                titles(PLEZY) shouldBe listOf("movie")
            }

            test("live events give no items") {
                repeat(8) { provider.watchNext += daznEvent("Match $it") }
                tick(DAZN)

                items(DAZN).shouldBeEmpty()
            }

            test("watchlist entries are dropped") {
                provider.watchNext += episode("watchlist", WatchNextPrograms.WATCH_NEXT_TYPE_WATCHLIST)
                provider.watchNext += episode("new", WatchNextPrograms.WATCH_NEXT_TYPE_NEW)
                tick(PLEZY)

                items(PLEZY).map { it.title to it.kind } shouldBe listOf("new" to ShelfKind.New)
            }

            test("a continue item at 95 percent or more is dropped") {
                fun at(
                    title: String,
                    position: Int,
                    watchNextType: Int = WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE,
                ) = plezy(
                    title,
                    NOW - DAY,
                    WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS to position,
                    WatchNextPrograms.COLUMN_DURATION_MILLIS to 1_000,
                    WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE to watchNextType,
                )
                provider.watchNext += at("at 94", 940)
                provider.watchNext += at("at 95", 950)
                provider.watchNext += at("at 99", 990)
                provider.watchNext += at("next at 99", 990, WatchNextPrograms.WATCH_NEXT_TYPE_NEXT)
                tick(PLEZY)

                titles(PLEZY) shouldBe listOf("at 94", "next at 99")
            }

            test("an item engaged more than 30 days ago is dropped and one without a time stays") {
                provider.watchNext += plezy("31 days", NOW - 31 * DAY)
                provider.watchNext += plezy("29 days", NOW - 29 * DAY)
                provider.watchNext += watchNext(PLEZY, "no time", WatchNextPrograms.COLUMN_TYPE to WatchNextPrograms.TYPE_MOVIE)
                tick(PLEZY)

                titles(PLEZY) shouldBe listOf("29 days", "no time")
            }

            test("one item per series stays, the most recent") {
                provider.watchNext += plezy("Severance", NOW - 3 * DAY, WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER to "3")
                provider.watchNext += plezy("Severance", NOW - DAY, WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER to "4")
                provider.watchNext += plezy("Old name", NOW - 4 * DAY, "series_id" to "s-1")
                provider.watchNext += plezy("New name", NOW - 2 * DAY, "series_id" to "s-1")
                tick(PLEZY)

                items(PLEZY).map { it.title to it.episodeNumber } shouldBe listOf("Severance" to "4", "New name" to null)
            }

            test("at most five items per app, the most recently engaged first") {
                listOf(4, 1, 7, 2, 6, 3, 5).forEach { days -> provider.watchNext += plezy("$days days", NOW - days * DAY) }
                tick(PLEZY)

                titles(PLEZY) shouldBe listOf("1 days", "2 days", "3 days", "4 days", "5 days")
            }

            test("the publishers are the installed apps by label, with their watch next and channel counts") {
                install(PLEZY, "Plezy")
                install(DAZN, "DAZN")
                install(SMARTTUBE, "SmartTube")
                install(JELLYFIN, "Jellyfin")
                repeat(3) { provider.watchNext += plezy("Show $it") }
                repeat(2) { provider.watchNext += daznEvent("Match $it") }
                provider.watchNext += watchNext(JELLYFIN, "Dune")
                provider.watchNext += watchNext("com.gone", "Uninstalled")
                smartTube()
                tick()

                publishers() shouldBe
                    listOf(
                        ShelfPublisher(DAZN, "DAZN", 2, emptyList()),
                        ShelfPublisher(JELLYFIN, "Jellyfin", 1, emptyList()),
                        ShelfPublisher(PLEZY, "Plezy", 3, emptyList()),
                        ShelfPublisher(
                            SMARTTUBE,
                            "SmartTube",
                            0,
                            listOf(
                                PublishedChannel(ShelfChannel("history", "Historial"), 3),
                                PublishedChannel(ShelfChannel("recommended", "Recomendados"), 2),
                                PublishedChannel(ShelfChannel("subscriptions", "Suscripciones"), 0),
                            ),
                        ),
                    )
            }

            test("a provider that refuses gives nothing") {
                install(PLEZY, "Plezy")
                provider.watchNext += plezy("Severance")
                provider.refuse = true
                tick(PLEZY)

                items(PLEZY).shouldBeEmpty()
                publishers().shouldBeEmpty()
            }
        }
    })
