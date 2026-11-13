package dev.niltsiar.tvhome.core.model

import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.integration.kotest.assertions.kotestAssertionsSupport

class ModelTestSession : TestSession(testConfig = DefaultConfiguration.kotestAssertionsSupport())
