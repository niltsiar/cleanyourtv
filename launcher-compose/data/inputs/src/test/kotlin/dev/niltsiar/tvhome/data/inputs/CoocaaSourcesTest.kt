package dev.niltsiar.tvhome.data.inputs

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowContentResolver

private const val PRIMARY = "com.skyworth.tv.provider"
private const val SDK = "com.skyworth.tv.sdk.provider"

/** A source_list provider with [rows] of name, index and label; like the sdk one, it refuses a query without a projection. */
private class FakeSourceList(
    private val rows: List<Array<Any?>>,
    private val fails: Boolean = false,
) : ContentProvider() {
    var queries = 0

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        queries += 1
        if (fails || projection == null) throw IllegalArgumentException("no source list")
        return MatrixCursor(projection).apply { rows.forEach(::addRow) }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ) = 0
}

val CoocaaSourcesTest by testSuite {
    robolectricTestSuite<CoocaaSourcesTestContent>("default sdk")
}

class CoocaaSourcesTestContent :
    RobolectricTestSuiteContent({
        fun provide(
            authority: String,
            provider: FakeSourceList,
        ): FakeSourceList = provider.also { ShadowContentResolver.registerProviderInternal(authority, it) }

        fun list() = CoocaaSources(RuntimeEnvironment.getApplication()).list()

        val sdkRows =
            listOf<Array<Any?>>(arrayOf("VOD", 0, "TV"), arrayOf("HDMI", 2, ""), arrayOf("HDMI", 1, "HDMI 1"), arrayOf("AV", 0, null))
        val sdkSources =
            listOf(
                CoocaaSource("HDMI", 1, "HDMI 1"),
                CoocaaSource("HDMI", 2, "HDMI"),
                CoocaaSource("AV", 0, "AV"),
                CoocaaSource("VOD", 0, "TV"),
            )

        test("an empty primary source list falls back to the sdk provider") {
            provide(PRIMARY, FakeSourceList(emptyList()))
            provide(SDK, FakeSourceList(sdkRows))

            list() shouldBe sdkSources
        }

        test("a missing or failing primary source list falls back to the sdk provider") {
            provide(SDK, FakeSourceList(sdkRows))
            list() shouldBe sdkSources

            provide(PRIMARY, FakeSourceList(emptyList(), fails = true))
            list() shouldBe sdkSources
        }

        test("a primary source list with rows is used and the sdk provider is not asked") {
            provide(PRIMARY, FakeSourceList(listOf(arrayOf("HDMI", 3, "Console"), arrayOf("VOD", 0, "TV"))))
            val sdk = provide(SDK, FakeSourceList(sdkRows))

            list() shouldBe listOf(CoocaaSource("HDMI", 3, "Console"), CoocaaSource("VOD", 0, "TV"))
            sdk.queries shouldBe 0
        }
    })
