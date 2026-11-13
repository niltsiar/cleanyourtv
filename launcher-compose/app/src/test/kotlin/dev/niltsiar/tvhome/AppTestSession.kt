package dev.niltsiar.tvhome

import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.integration.kotest.assertions.kotestAssertionsSupport

class AppTestSession : TestSession(testConfig = DefaultConfiguration.kotestAssertionsSupport())
