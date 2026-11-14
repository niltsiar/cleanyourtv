package dev.niltsiar.tvhome.posters

import android.content.ContentValues
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Process
import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectric
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowApplicationPackageManager
import org.robolectric.shadows.ShadowBinder
import java.io.FileNotFoundException

private const val HOME_UID = 10_077
private const val OTHER_UID = 10_088

/** Compares the signatures of the uids' packages, where Robolectric's own answer is always a match. */
@Implements(className = "android.app.ApplicationPackageManager", isInAndroidSdk = false)
class ShadowUidSignatures : ShadowApplicationPackageManager() {
    @Implementation
    override fun checkSignatures(
        uid1: Int,
        uid2: Int,
    ): Int {
        val first = getPackagesForUid(uid1)?.firstOrNull() ?: return PackageManager.SIGNATURE_UNKNOWN_PACKAGE
        val second = getPackagesForUid(uid2)?.firstOrNull() ?: return PackageManager.SIGNATURE_UNKNOWN_PACKAGE
        return checkSignatures(first, second)
    }
}

private object OwnSandbox

/** Who may open the helper's provider, and how. */
val PosterProviderTest by testSuite {
    robolectricTestSuite<PosterProviderTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                shadows += ShadowUidSignatures::class
                portableClasses += OwnSandbox::class
            },
    )
}

class PosterProviderTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object {
                val app = RuntimeEnvironment.getApplication()
                val provider = Robolectric.setupContentProvider(PosterProvider::class.java, POSTER_AUTHORITY)
                val poster: Uri =
                    Uri.parse("content://dev.niltsiar.tvhome.posters/poster?u=https%3A%2F%2Fposters.test%2Fp.jpg&w=480&h=270")

                init {
                    provider.posters = Posters(app, executor = {})
                    val packages = shadowOf(app.packageManager)
                    packages.getInternalMutablePackageInfo(app.packageName).signatures = arrayOf(Signature("0a0b0c"))
                    packages.setPackagesForUid(Process.myUid(), app.packageName)

                    fun install(
                        name: String,
                        uid: Int,
                        key: String,
                    ) {
                        packages.installPackage(
                            PackageInfo().apply {
                                packageName = name
                                signatures = arrayOf(Signature(key))
                                applicationInfo = ApplicationInfo().apply { packageName = name }
                            },
                        )
                        packages.setPackagesForUid(uid, name)
                    }
                    install("dev.niltsiar.tvhome", HOME_UID, "0a0b0c")
                    install("com.example.other", OTHER_UID, "0d0e0f")
                }
            }
        } closeWith { ShadowBinder.reset() } asContextForEach {
            test("a caller signed with another key is refused, and Home's key gets an answer") {
                ShadowBinder.setCallingUid(OTHER_UID)
                shouldThrow<SecurityException> { provider.openFile(poster, "r") }

                ShadowBinder.setCallingUid(HOME_UID)
                shouldThrow<FileNotFoundException> { provider.openFile(poster, "r") }
            }

            test("write modes, query and every write call are refused") {
                ShadowBinder.setCallingUid(HOME_UID)
                for (mode in listOf("w", "wa", "rw", "rwt")) {
                    withClue(mode) { shouldThrow<SecurityException> { provider.openFile(poster, mode) } }
                }
                provider.query(poster, null, null, null, null).shouldBeNull()
                provider.getType(poster).shouldBeNull()
                shouldThrow<UnsupportedOperationException> { provider.insert(poster, ContentValues()) }
                shouldThrow<UnsupportedOperationException> { provider.update(poster, ContentValues(), null, null) }
                shouldThrow<UnsupportedOperationException> { provider.delete(poster, null, null) }
            }
        }
    })
