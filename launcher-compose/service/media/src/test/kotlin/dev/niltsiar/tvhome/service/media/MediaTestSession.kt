package dev.niltsiar.tvhome.service.media

import de.infix.testBalloon.framework.core.TestSession
import de.infix.testBalloon.integration.kotest.assertions.kotestAssertionsSupport

class MediaTestSession : TestSession(testConfig = DefaultConfiguration.kotestAssertionsSupport())
