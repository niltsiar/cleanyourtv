# Local unit tests are TestBalloon suites with Kotest assertions

Status: accepted

## Context

The local unit tests (`testDebugUnitTest`) cover plain Kotlin code, Robolectric code and Compose UI on Robolectric with a
Compose test rule. The instrumented code in `baselineprofile` (Macrobenchmark and the
baseline profile generator) runs on a device through AndroidX Test, which needs JUnit 4.

## Decision

Every local unit test is a [TestBalloon](https://infix-de.github.io/testBalloon/) suite with
[Kotest assertions](https://kotest.io/docs/assertions/assertions.html). JUnit stays only where Android or a library needs
it:

- instrumented tests, such as the Macrobenchmark and baseline profile code in `baselineprofile`;
- JUnit 4 rules inside a suite, such as the Compose test rule, `RuleChain` and `TemporaryFolder`, through TestBalloon's
  `JUnit4RulesContext`.

The reasons:

- The launcher is written in Kotlin and Compose, and its tests are too: TestBalloon is a Kotlin framework whose tests
  are suspend lambdas found by a compiler plugin, where JUnit 4 is a Java framework of annotated methods found by
  reflection.
- A test name is a sentence in a string, and a suite is a Kotlin value. Shared state lives in a fixture that each test
  gets new, in place of fields, `lateinit` and `@Before`.
- TestBalloon runs Robolectric suites and JUnit 4 rules, so the Compose UI tests keep `createComposeRule()`,
  `waitUntil` and `mainClock`.
- A Kotest matcher names the values it compared when it fails, and `withClue` adds context to any assertion.

## Consequences

- A module turns TestBalloon on with the `tvhome.android.testballoon` plugin and one `TestSession` in its test sources.
  [`docs/testing.md`](../testing.md) shows how a test is written.
- A Robolectric test is a `robolectricTestSuite` with a content class, with its `@Config` values as
  `TestConfig.robolectric` settings.
- TestBalloon does not read `@GraphicsMode`. A module whose tests need the native canvas sets
  `robolectric.graphicsMode` to `NATIVE` for its test task, and that applies to every suite of the module.
- Each test has a 60 s limit by default. A longer test sets its own limit with `TestConfig.testScope`.
- The XML results keep only the test name, without the suite path, so each test name is unique in its module.
- `checkNoJUnitTests` runs before every unit test task of an app or library module and fails the build when a file
  under `src/test` holds a JUnit test (`org.junit.Test`, `@Test` or `@RunWith`). The JUnit 4 runner stays on the test
  classpath for the rules, so without this check a JUnit class would run beside the suites.
