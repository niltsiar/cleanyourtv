package dev.niltsiar.tvhome.core.designsystem

import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.integration.kotest.assertions.kotestAssertionsSupport

class DesignSystemTestSession : TestSession(testConfig = DefaultConfiguration.kotestAssertionsSupport())
