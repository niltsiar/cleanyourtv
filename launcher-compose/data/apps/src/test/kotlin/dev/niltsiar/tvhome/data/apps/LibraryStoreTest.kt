package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.JUnit4RulesContext
import de.infix.testBalloon.framework.core.testSuite
import de.infix.testBalloon.integration.robolectric.RobolectricTestSuiteContent
import de.infix.testBalloon.integration.robolectric.robolectricTestSuite
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.rules.TemporaryFolder
import java.io.File

val LibraryStoreTest by testSuite {
    robolectricTestSuite<LibraryStoreTestContent>("default sdk")
}

class LibraryStoreTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val folder = rule(TemporaryFolder())

                val store get() = LibraryStore(folder.root)

                fun file(name: String) = File(folder.root, name)
            }
        } asContextForEach {
            test("without files nothing is chosen") {
                store.load() shouldBe LibraryChoices()
            }

            test("adding an app writes it sorted and clears an earlier removal") {
                file(LIBRARY_REMOVED_FILE).writeText("com.b\ncom.z\n")
                file(LIBRARY_ADDED_FILE).writeText("com.y\n")

                store.save("com.b", onHome = true).shouldBeTrue()

                file(LIBRARY_ADDED_FILE).readText() shouldBe "com.b\ncom.y\n"
                file(LIBRARY_REMOVED_FILE).readText() shouldBe "com.z\n"
                store.load() shouldBe LibraryChoices(added = setOf("com.b", "com.y"), removed = setOf("com.z"))
            }

            test("removing an app records the removal and drops an earlier addition") {
                file(LIBRARY_ADDED_FILE).writeText("com.a\ncom.b\n")

                store.save("com.a", onHome = false).shouldBeTrue()

                file(LIBRARY_REMOVED_FILE).readText() shouldBe "com.a\n"
                file(LIBRARY_ADDED_FILE).readText() shouldBe "com.b\n"
            }

            test("a file that cannot be read stays as it is and nothing is written") {
                file(LIBRARY_ADDED_FILE).writeText("not a package\n")

                store.save("com.a", onHome = true).shouldBeFalse()
                store.save("com.a", onHome = false).shouldBeFalse()

                file(LIBRARY_ADDED_FILE).readText() shouldBe "not a package\n"
                file(LIBRARY_REMOVED_FILE).exists().shouldBeFalse()
                store.load() shouldBe LibraryChoices()
            }

            test("a file over the size limit is not read") {
                file(LIBRARY_REMOVED_FILE).writeText("a".repeat(MAX_ADDED * 256 + 1))

                store.save("com.a", onHome = true).shouldBeFalse()

                file(LIBRARY_ADDED_FILE).exists().shouldBeFalse()
            }

            test("an app is not saved past the limit or under an invalid name") {
                file(LIBRARY_ADDED_FILE).writeText((0 until MAX_ADDED).joinToString("") { "com.example.app$it\n" })

                store.save("com.example.extra", onHome = true).shouldBeFalse()
                store.save("../escape", onHome = true).shouldBeFalse()

                store.load().added shouldHaveSize MAX_ADDED
            }

            test("saves at the same moment all persist") {
                val shared = store
                val packages = (1..32).map { "com.app$it" }

                runBlocking {
                    val go = CompletableDeferred<Unit>()
                    val saves = packages.map { pkg -> async(Dispatchers.IO) { go.await().let { shared.save(pkg, onHome = true) } } }
                    go.complete(Unit)
                    saves.awaitAll().all { it }.shouldBeTrue()
                }

                shared.load().added shouldBe packages.toSet()
            }
        }
    })
