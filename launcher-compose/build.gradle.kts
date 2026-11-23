import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import nl.littlerobots.vcu.plugin.versionSelector

plugins {
    id("tvhome.quality")
    alias(libs.plugins.versions)
    alias(libs.plugins.versionCatalogUpdate)
}

private val prereleaseToken = Regex("(alpha|beta|rc|cr|m|milestone|preview|eap|dev|snapshot)\\d*", RegexOption.IGNORE_CASE)

fun isPrerelease(version: String): Boolean = version.split('.', '-', '_', '+').any(prereleaseToken::matches)

// A prerelease is proposed only for a dependency that is already on one.
fun isUpgrade(
    current: String,
    candidate: String,
): Boolean = !isPrerelease(candidate) || isPrerelease(current)

tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf { !isUpgrade(currentVersion, candidate.version) }
}

versionCatalogUpdate {
    sortByKey = false
    groupVersionRefs = false
    // ktlint has no library entry: Spotless reads its version from the catalog.
    keep { keepUnusedVersions = true }
    versionSelector { isUpgrade(it.currentVersion, it.candidate.version) }
}
