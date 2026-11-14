package dev.niltsiar.tvhome.feature.library

import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.integration.kotest.assertions.kotestAssertionsSupport

class LibraryTestSession : TestSession(testConfig = DefaultConfiguration.kotestAssertionsSupport())
