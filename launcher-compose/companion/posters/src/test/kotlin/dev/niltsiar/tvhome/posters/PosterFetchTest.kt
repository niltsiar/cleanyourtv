package dev.niltsiar.tvhome.posters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.UnknownHostException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

private const val HOST = "posters.test"

/** Sends every connection to [server], whatever address the helper resolved, as a network with that address would. */
private class ToServer(
    private val server: () -> InetSocketAddress,
) : SocketFactory() {
    override fun createSocket(): Socket =
        object : Socket() {
            override fun connect(
                endpoint: SocketAddress,
                timeout: Int,
            ) = super.connect(server(), timeout)
        }

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket = throw UnsupportedOperationException()

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = throw UnsupportedOperationException()

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket = throw UnsupportedOperationException()

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket = throw UnsupportedOperationException()
}

/** Fetches from a local server over TLS, with the production URL, address, HTTP and picture rules. */
val PosterFetchTest by testSuite {
    robolectricTestSuite<PosterFetchTestContent>("sdk 31", testConfig = TestConfig.robolectric { sdk = 31 })
}

class PosterFetchTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app = RuntimeEnvironment.getApplication()
                val server = MockWebServer()
                val fetches = ArrayDeque<Runnable>()
                var time = 1_800_000_000_000L

                /** The address the test network's DNS gives [HOST]. */
                var hostAddress = "203.0.113.10"

                private val root = HeldCertificate.Builder().certificateAuthority(0).build()
                private val serverCertificates =
                    HandshakeCertificates
                        .Builder()
                        .heldCertificate(
                            HeldCertificate
                                .Builder()
                                .addSubjectAlternativeName(HOST)
                                .signedBy(root)
                                .build(),
                        ).build()
                private val trusted = HandshakeCertificates.Builder().addTrustedCertificate(root.certificate).build()

                private val resolver =
                    Dns { host ->
                        if (host != HOST) throw UnknownHostException(host)
                        listOf(InetAddress.getByAddress(host, InetAddress.getByName(hostAddress).address))
                    }

                val provider: PosterProvider =
                    Robolectric.setupContentProvider(PosterProvider::class.java, POSTER_AUTHORITY).apply {
                        posters =
                            Posters(
                                app,
                                PosterCache(File(app.cacheDir, "posters")),
                                PosterFetcher(resolver) {
                                    OkHttpClient
                                        .Builder()
                                        .socketFactory(ToServer { server.socketAddress })
                                        .sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager)
                                        .build()
                                },
                                Executor { fetches.addLast(it) },
                            ) { time }
                    }

                fun serve(
                    vararg responses: MockResponse,
                    https: Boolean = true,
                ) {
                    if (https) server.useHttps(serverCertificates.sslSocketFactory())
                    responses.forEach(server::enqueue)
                    server.start()
                }

                fun image(
                    bytes: ByteArray,
                    type: String = "image/jpeg",
                ) = MockResponse
                    .Builder()
                    .addHeader("Content-Type", type)
                    .body(Buffer().write(bytes))
                    .build()

                fun picture(
                    width: Int,
                    height: Int,
                    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
                ): ByteArray {
                    val out = ByteArrayOutputStream()
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(format, 90, out)
                    return out.toByteArray()
                }

                fun poster(
                    url: String = "https://$HOST/p.jpg",
                    lan: Boolean = false,
                ): Uri =
                    Uri
                        .Builder()
                        .scheme("content")
                        .authority("dev.niltsiar.tvhome.posters")
                        .path("poster")
                        .appendQueryParameter("u", url)
                        .appendQueryParameter("w", "1920")
                        .appendQueryParameter("h", "1080")
                        .apply { if (lan) appendQueryParameter("lan", "1") }
                        .build()

                fun open(uri: Uri = poster()) = app.contentResolver.openFileDescriptor(uri, "r")

                fun opened(uri: Uri = poster()): Bitmap =
                    open(uri).shouldNotBeNull().use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor) }.shouldNotBeNull()

                fun runFetches() {
                    while (fetches.isNotEmpty()) fetches.removeFirst().run()
                }

                /** The poster is not served after its fetch ran, and Home was not told of a change. */
                fun notLanded(uri: Uri = poster()) {
                    runFetches()
                    shouldThrow<FileNotFoundException> { open(uri) }
                    shadowOf(app.contentResolver).notifiedUris.shouldBeEmpty()
                }
            }
        } closeWith { server.close() } asContextForEach {
            test("a miss throws at once and queues one fetch") {
                serve(image(picture(1600, 900)))

                shouldThrow<FileNotFoundException> { open() }

                server.requestCount shouldBe 0
                fetches.size shouldBe 1
            }

            test("a good JPEG lands at most 640 x 360 and is served on the next open") {
                serve(image(picture(1600, 1200)))
                shouldThrow<FileNotFoundException> { open() }

                runFetches()
                val stored = opened()

                stored.width shouldBeLessThanOrEqual 640
                stored.height shouldBeLessThanOrEqual 360
                (stored.width to stored.height) shouldBe (640 to 360)
            }

            test("a landed fetch notifies the URI Home watches") {
                serve(image(picture(1600, 900)))
                shouldThrow<FileNotFoundException> { open() }

                runFetches()

                shadowOf(app.contentResolver).notifiedUris.map { it.uri.toString() } shouldContainExactly
                    listOf("content://dev.niltsiar.tvhome.posters/poster")
            }

            test("an http poster on a private address lands with lan") {
                hostAddress = "192.168.1.20"
                serve(image(picture(1600, 900)), https = false)
                val uri = poster("http://$HOST:8096/p.jpg", lan = true)
                shouldThrow<FileNotFoundException> { open(uri) }

                runFetches()

                opened(uri).width shouldBe 640
            }

            test("a host on a private address is not fetched without lan") {
                hostAddress = "192.168.1.20"
                serve(image(picture(1600, 900)))
                shouldThrow<FileNotFoundException> { open() }

                notLanded()
                server.requestCount shouldBe 0
            }

            test("a host on a public address is not fetched with lan over http") {
                hostAddress = "203.0.113.10"
                serve(image(picture(1600, 900)), https = false)
                val uri = poster("http://$HOST/p.jpg", lan = true)
                shouldThrow<FileNotFoundException> { open(uri) }

                notLanded(uri)
                server.requestCount shouldBe 0
            }

            test("a redirect is a failed fetch and is not followed") {
                serve(
                    MockResponse
                        .Builder()
                        .code(302)
                        .addHeader("Location", "https://$HOST/other.jpg")
                        .build(),
                    image(picture(1600, 900)),
                )
                shouldThrow<FileNotFoundException> { open() }

                notLanded()
                server.requestCount shouldBe 1
            }

            test("a 3 MiB body is refused") {
                serve(image(picture(1600, 900) + ByteArray(3 * 1024 * 1024)))
                shouldThrow<FileNotFoundException> { open() }

                notLanded()
            }

            test("a content type other than jpeg, png or webp is refused") {
                serve(image(picture(1600, 900), type = "image/gif"))
                shouldThrow<FileNotFoundException> { open() }

                notLanded()
            }

            test("a 5000 px wide picture is refused") {
                serve(image(picture(5000, 300, Bitmap.CompressFormat.PNG), type = "image/png"))
                shouldThrow<FileNotFoundException> { open() }

                notLanded()
            }

            test("a failed URL is not fetched again within the hour") {
                serve(MockResponse.Builder().code(404).build())
                shouldThrow<FileNotFoundException> { open() }
                runFetches()

                time += TimeUnit.MINUTES.toMillis(59)
                shouldThrow<FileNotFoundException> { open() }
                fetches.size shouldBe 0

                time += TimeUnit.MINUTES.toMillis(1)
                shouldThrow<FileNotFoundException> { open() }
                fetches.size shouldBe 1
            }

            test("a poster older than 7 days is served and fetched again") {
                serve(image(picture(1600, 900)), image(picture(1600, 900)))
                shouldThrow<FileNotFoundException> { open() }
                runFetches()

                time += TimeUnit.DAYS.toMillis(7)
                opened()
                fetches.size shouldBe 0

                time += 1
                opened()
                fetches.size shouldBe 1
            }

            test("one fetch per poster, and at most 2 running and 32 waiting") {
                shouldThrow<FileNotFoundException> { open() }
                shouldThrow<FileNotFoundException> { open() }
                fetches.size shouldBe 1

                for (index in 1..40) shouldThrow<FileNotFoundException> { open(poster("https://$HOST/$index.jpg")) }

                fetches.size shouldBe 34
            }
        }
    })
