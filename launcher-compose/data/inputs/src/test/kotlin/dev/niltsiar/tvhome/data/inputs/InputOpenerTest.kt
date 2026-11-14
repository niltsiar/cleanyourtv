package dev.niltsiar.tvhome.data.inputs

import android.content.Intent
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import dev.niltsiar.tvhome.core.config.TvConfig
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.MutableStateFlow

private const val PORT = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5"
private const val CHANNEL_TYPE = "vnd.android.cursor.item/channel"

val InputOpenerTest by testSuite {
    robolectricTestSuite<InputOpenerTestContent>("default sdk")
}

class InputOpenerTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val started = mutableListOf<Intent>()
                var refuse: (Intent) -> Boolean = { false }

                fun open(
                    config: TvConfig = TvConfig(liveTvPackage = null),
                    id: String = PORT,
                    parent: String? = null,
                ): Boolean =
                    InputOpener(MutableStateFlow(config)) { intent ->
                        started += intent
                        !refuse(intent)
                    }.open(id, parent)
            }
        } asContextForEach {
            test("the standard way is a view of the channel uri with a mime type") {
                open().shouldBeTrue()

                val view = started.single()
                view.action shouldBe Intent.ACTION_VIEW
                view.dataString shouldBe "content://android.media.tv/passthrough/" + android.net.Uri.encode(PORT)
                view.type shouldBe CHANNEL_TYPE
                (view.flags and Intent.FLAG_ACTIVITY_NEW_TASK) shouldNotBe 0
            }

            test("the live tv package is tried first so that no chooser shows and then anything") {
                refuse = { it.`package` != null }

                open(TvConfig(liveTvPackage = "com.tv.live")).shouldBeTrue()

                started.map { it.`package` } shouldBe listOf<String?>("com.tv.live", null)
            }

            test("a refusal does not stop the fallbacks and a total refusal returns false") {
                refuse = { true }

                open(TvConfig(liveTvPackage = "com.tv.live")).shouldBeFalse()

                started shouldHaveSize 2
            }

            test("an input action gets the same uri where nothing takes the view") {
                refuse = { it.action == Intent.ACTION_VIEW }

                open(TvConfig(liveTvPackage = null, inputAction = "com.xgimi.action.hdmiPlayer")).shouldBeTrue()

                val last = started.last()
                last.action shouldBe "com.xgimi.action.hdmiPlayer"
                last.type shouldBe CHANNEL_TYPE
                last.data shouldBe started.first().data
            }

            test("a mapped input sends the input action with its source number") {
                val config =
                    TvConfig(liveTvPackage = null, inputAction = "com.xiaomi.mitv.tvplayer.EXTSRC_PLAY", inputSources = listOf("HW5=23"))

                open(config).shouldBeTrue()

                val sent = started.single()
                sent.action shouldBe "com.xiaomi.mitv.tvplayer.EXTSRC_PLAY"
                sent.getIntExtra("input", -1) shouldBe 23
            }

            test("a cec device uses the source number of its port but opens its own input") {
                val config = TvConfig(liveTvPackage = null, inputAction = "vendor.PLAY", inputSources = listOf("HW5=23"))
                refuse = { it.action == "vendor.PLAY" }

                open(config, id = "com.mediatek.tvinput/.cec/CEC7", parent = PORT).shouldBeTrue()

                started.map { it.action } shouldBe listOf("vendor.PLAY", Intent.ACTION_VIEW)
                started[0].getIntExtra("input", -1) shouldBe 23
                started[1].dataString!! shouldContain "CEC7"
            }

            test("an activity in input action starts itself ahead of the standard way for unmapped inputs") {
                val config = TvConfig(liveTvPackage = null, inputAction = "com.xiaomi.mitv.tvplayer/.ExternalSourceActivity")

                open(config).shouldBeTrue()

                val sent = started.single()
                sent.component?.packageName shouldBe "com.xiaomi.mitv.tvplayer"
                sent.data.shouldBeNull()
            }

            test("a failed vendor picker falls through to the standard way") {
                refuse = { it.component != null }

                open(TvConfig(liveTvPackage = null, inputAction = "com.xiaomi.mitv.tvplayer/.ExternalSourceActivity")).shouldBeTrue()

                started.last().action shouldBe Intent.ACTION_VIEW
            }

            test("an input mapped to a dash skips the vendor route and a scheme replaces the uri scheme and the type") {
                val config =
                    TvConfig(liveTvPackage = null, inputAction = "vendor.PLAY", inputSources = listOf("HW5=-"), inputUriScheme = "live-tv")

                open(config).shouldBeTrue()

                val view = started.single()
                view.action shouldBe Intent.ACTION_VIEW
                view.data?.scheme shouldBe "live-tv"
                view.type.shouldBeNull()
            }
        }
    })
