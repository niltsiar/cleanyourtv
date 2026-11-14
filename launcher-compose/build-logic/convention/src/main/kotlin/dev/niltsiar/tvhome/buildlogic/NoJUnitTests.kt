package dev.niltsiar.tvhome.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test

/** Fails when a Kotlin or Java file in [sources] holds a JUnit test. The JUnit 4 runner stays on the test classpath for the rules. */
abstract class CheckNoJUnitTests : DefaultTask() {
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:OutputFile
    abstract val marker: RegularFileProperty

    @TaskAction
    fun check() {
        val root = rootDirectory.get().asFile
        val found =
            sources.files.sorted().flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (JUNIT_TEST.containsMatchIn(line)) "  ${file.relativeTo(root)}:${index + 1}: ${line.trim()}" else null
                }
            }
        if (found.isNotEmpty()) {
            throw GradleException(
                (listOf("JUnit tests in src/test:") + found).joinToString("\n") +
                    "\nLocal unit tests are TestBalloon suites with Kotest assertions: see docs/testing.md.",
            )
        }
        marker.get().asFile.writeText("")
    }
}

private val JUNIT_TEST = Regex("""org\.junit\.Test\b|@Test\b|@RunWith\b""")

/** Every unit test task of the module runs [CheckNoJUnitTests] over `src/test` first. */
internal fun Project.checkNoJUnitTests() {
    val check =
        tasks.register("checkNoJUnitTests", CheckNoJUnitTests::class.java) {
            sources.from(
                layout.projectDirectory
                    .dir("src/test")
                    .asFileTree
                    .matching { include("**/*.kt", "**/*.java") },
            )
            rootDirectory.set(rootProject.layout.projectDirectory)
            marker.set(layout.buildDirectory.file("checkNoJUnitTests/passed"))
        }
    tasks.withType(Test::class.java).configureEach { dependsOn(check) }
}
