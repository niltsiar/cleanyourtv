package dev.niltsiar.tvhome.feature.home

import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.integration.kotest.assertions.kotestAssertionsSupport

class HomeTestSession : TestSession(testConfig = DefaultConfiguration.kotestAssertionsSupport())
