package dev.niltsiar.tvhome.data.system

import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.integration.kotest.assertions.kotestAssertionsSupport

class SystemTestSession : TestSession(testConfig = DefaultConfiguration.kotestAssertionsSupport())
