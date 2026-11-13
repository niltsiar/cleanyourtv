package dev.niltsiar.tvhome.core.config

import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.integration.kotest.assertions.kotestAssertionsSupport

class ConfigTestSession : TestSession(testConfig = DefaultConfiguration.kotestAssertionsSupport())
