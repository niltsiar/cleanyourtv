package dev.niltsiar.tvhome.data.apps

import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.integration.kotest.assertions.kotestAssertionsSupport

class AppsTestSession : TestSession(testConfig = DefaultConfiguration.kotestAssertionsSupport())
