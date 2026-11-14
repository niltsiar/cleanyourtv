package dev.niltsiar.tvhome.data.inputs

import android.content.ComponentName
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

private const val TUNER = "com.mediatek.tvinput/.tuner.TunerInputService/HW1"
private val Handler = ComponentName("com.mediatek.wwtv.tvcenter", "com.mediatek.wwtv.tvcenter.nav.TurnkeyUiMainActivity")

val LiveTvTest by testSuite {
    robolectricTestSuite<LiveTvTestContent>("default sdk")
}

class LiveTvTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app = Intent(Intent.ACTION_MAIN).setPackage("com.mediatek.wwtv.tvcenter")

                fun tuned(
                    tunerId: String? = TUNER,
                    inputId: String? = null,
                    handler: ComponentName? = Handler,
                ) = tunedIntent(app, tunerId, "com.mediatek.wwtv.tvcenter", inputId) { handler }
            }
        } asContextForEach {
            test("the automatic tuner is the passthrough channel of the tuner in the live tv package") {
                val intent = tuned()

                intent.action shouldBe Intent.ACTION_VIEW
                intent.data shouldBe TvContract.buildChannelUriForPassthroughInput(TUNER)
                intent.type shouldBe TvContract.Channels.CONTENT_ITEM_TYPE
                intent.`package` shouldBe "com.mediatek.wwtv.tvcenter"
            }

            test("a configured tuner opens its channel collection") {
                val intent = tuned(inputId = TUNER)

                intent.data shouldBe TvContract.buildChannelsUriForInput(TUNER)
                intent.type shouldBe TvContract.Channels.CONTENT_TYPE
            }

            test("the root channel uri goes to the component that handles the tuned intent") {
                val intent = tuned(inputId = TvContract.Channels.CONTENT_URI.toString())

                intent.action shouldBe Intent.ACTION_VIEW
                intent.data shouldBe TvContract.Channels.CONTENT_URI
                intent.component shouldBe Handler
                intent.`package`.shouldBeNull()
            }

            test("the tile path stays where the live tv package does not handle the tuner uri or there is no tuner") {
                tuned(handler = null) shouldBeSameInstanceAs app
                tuned(tunerId = null) shouldBeSameInstanceAs app
                tuned(tunerId = null, inputId = TvContract.Channels.CONTENT_URI.toString()) shouldBeSameInstanceAs app
                TvContract.Channels.CONTENT_URI shouldBe Uri.parse("content://android.media.tv/channel")
            }
        }
    })
