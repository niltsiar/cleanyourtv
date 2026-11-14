# Unit tests with TestBalloon and Kotest assertions

Local unit tests (`testDebugUnitTest`) use [TestBalloon](https://infix-de.github.io/testBalloon/) suites with
[Kotest assertions](https://kotest.io/docs/assertions/assertions.html). JUnit 4 classes in the same module keep running
beside the suites, so a module moves one class at a time.

## Turn it on in a module

1. Add the convention plugin to the module's `build.gradle.kts`:

   ```kotlin
   plugins {
       id("tvhome.android.testballoon")
   }
   ```

   It applies the `de.infix.testBalloon` Gradle plugin and adds the TestBalloon core, its Robolectric and Kotest
   integrations, and `kotest-assertions-core` to `testImplementation`. Keep `libs.junit`, `libs.robolectric` and the
   Compose test dependencies the module already has.

2. Add one test session to the module's test sources, in the module's test package:

   ```kotlin
   class AppTestSession : TestSession(testConfig = DefaultConfiguration.kotestAssertionsSupport())
   ```

   `kotestAssertionsSupport()` makes `withClue` and `assertSoftly` safe when a test's coroutine changes thread.

Do not enable JUnit Platform or JUnit Vintage on these test tasks, and do not set `maxParallelForks`: TestBalloon runs
through the JUnit 4 runner on Android host tests.

## A JUnit class becomes a suite

| JUnit 4 | TestBalloon |
|---|---|
| `class FooTest { ... }` | `val FooTest by testSuite { ... }` (top-level property, same name) |
| `@Test fun the_thing_happens()` | `test("the thing happens") { ... }` |
| `private fun helper()` in the class | a local `fun helper()` inside the suite lambda |
| `@get:Rule val rule = ...`, `lateinit var` fields | a `testFixture { object : JUnit4RulesContext() { ... } } asContextForEach { ... }`; without rules, a plain `object { ... }` |
| a field that holds state a test changes, such as `val config = MutableStateFlow(...)`, a `Channel` or a fake | a property of the fixture object: a `val` in the suite lambda is one object for every test |
| `@Before` | the fixture object's `init` block; when it needs the rules running, the innermost link of a `RuleChain` (below) |
| `@After` | `closeWith { ... }` on the fixture: it runs after a failing test too. When it ran inside the rules, `after()` of the innermost `RuleChain` link |
| a field each test gets new, such as `val hold = OkHold()` | `testFixture { OkHold() } asParameterForEach { test("...") { hold -> ... } }` |
| a JUnit rule in a plain JVM class, such as `TemporaryFolder` | the same `JUnit4RulesContext` fixture, without Robolectric |
| a class nested in the test class | a local class at the top of the suite or content lambda; a private class in the file when a fixture class or a second content class uses it. A fixture `object` cannot declare a class |
| `@RunWith(RobolectricTestRunner::class)` and `@Config(...)` | `robolectricTestSuite<FooTestContent>(name, testConfig = TestConfig.robolectric { ... })` |
| `@Config(...)` on one test | a second `robolectricTestSuite` with its own content class and settings. Both content classes take their fixture from one `private class FooFixture : JUnit4RulesContext()` in the file (`SettingsScreenTest.kt` in `feature/settings`) |
| `@Config(qualifiers = "+night")` on one test | the class's qualifiers with `night` added in Android's order: `w960dp-h540dp-xhdpi` becomes `w960dp-h540dp-night-xhdpi` (`LibraryOverlayLookUiTest.kt` in `feature/library`) |
| `assumeTrue(...)` / `assumeFalse(...)` | TestBalloon reports a failed assumption as a failure. Decide in the content lambda, before the tests register, and pass `testConfig = TestConfig.disable()` to the tests to skip; the results count them as skipped (`SettingsPresenterTest.kt`) |
| a subclass that runs the same tests with one value changed | a loop over plain `testSuite` groups in one content class, with the value in each test name so names stay unique (`LibraryRecompositionTest.kt` in `feature/library`) |
| `fun the_thing() = runTest { ... }` | see [Coroutine tests](#coroutine-tests) |
| `fun the_thing() = runBlocking { ... }` | `test("the thing") { runBlocking { ... } }`, so the body keeps its own event loop on the test thread |

The test name keeps the words of the old method name, with spaces in place of underscores.

`spotlessApply` (ktlint) rejects a KDoc above a `test(...)` call: make it a `/* */` comment. It does not remove unused
imports, so remove the JUnit ones by hand.

### Plain JVM

```kotlin
val ConfigFocusStyleTest by testSuite {
    fun styleOf(json: String): FocusStyle = parseTvConfig(json).config.focusStyle()

    test("rim and halo reach the focus look") {
        styleOf("""{"glass_style": "rim"}""") shouldBe FocusStyle.Rim
    }
}
```

### Robolectric

The suite registers a Robolectric suite. Its tests live in a separate class, which Robolectric loads in its sandbox.
The `@Config` values in this project map to `TestConfig.robolectric` settings with the same name: `sdk = 31`,
`application = TvHomeApplication::class`, `qualifiers = "..."` and `shadows += ShadowFoo::class`. Each test gets a fresh
application, as with the JUnit runner.

```kotlin
val ClockActivityTest by testSuite {
    robolectricTestSuite<ClockActivityTestContent>(
        "sdk 31",
        testConfig =
            TestConfig.robolectric {
                sdk = 31
                application = TvHomeApplication::class
            },
    )
}

class ClockActivityTestContent :
    RobolectricTestSuiteContent({
        fun clock() = Robolectric.buildActivity(ClockActivity::class.java).create().start().resume()

        test("a remote motion closes the clock") {
            val activity = clock().get()
            activity.dispatchGenericMotionEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, 0f, 0f, 0)).shouldBeTrue()
            activity.isFinishing.shouldBeTrue()
        }
    })
```

The content lambda cannot see members of its own class, so helpers are local functions inside the lambda, or private
top-level functions in the file when two content classes share them (`LastOpenedTextTest.kt` in `core/model`). Robolectric
suites do not nest: put plain `testSuite` groups inside the content class instead. A `@Config` without `sdk` maps to
`robolectricTestSuite` without `testConfig`.

The content lambda runs in the sandbox while the tests register. If it reads a top-level `val` of its file there, the
file's class initializes again in the sandbox, registers its suite a second time, and the module fails with
`cannot register as a top-level suite inside TestSuite(...)`. Compute such a value in the content lambda. A `const val`,
and a top-level `val` that only test bodies or fixture functions read, are safe.

Suites with the same instrumentation share one sandbox, and TestBalloon sets a sandbox's shadows once, so the
`shadows +=` of one suite can apply to another suite or be missing from its own. A suite with shadows of its own adds a
private object of the file to `portableClasses`, which gives it a sandbox of its own (`KeyFilterPauseTest.kt` in
`service/keys`):

```kotlin
private object OwnSandbox

TestConfig.robolectric {
    sdk = 31
    shadows += ShadowVolumeSteps::class
    portableClasses += OwnSandbox::class
}
```

Robolectric reads no annotation on the content class: TestBalloon passes it `RobolectricTestSuiteContent`, so
`@GraphicsMode` has no effect, and `TestConfig.robolectric` has no graphics mode. A module whose tests need the native
canvas sets it for its test task, as `core/designsystem` does:

```kotlin
android {
    testOptions {
        unitTests.all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
    }
}
```

The property applies to every suite of the module. The native mode starts the window in touch mode, where Compose gives
nothing key focus, so a test that ran in the legacy mode and checks key focus leaves touch mode before `setContent`
(`SettingsScreenTest.kt` in `feature/settings`). With `createComposeRule()`, use
`createAndroidComposeRule<ComponentActivity>()` to reach the window.

```kotlin
compose.runOnUiThread {
    compose.activity.window.decorView
        .requestFocusFromTouch()
}
compose.setContent { /* ... */ }
```

A module that moves in several changes keeps JUnit classes beside its suites. A JUnit class without `@GraphicsMode` ran
in the legacy mode; once the module sets the property it runs in the native mode, and its key focus checks fail. Give
each such class `@GraphicsMode(GraphicsMode.Mode.LEGACY)` until it moves, as the annotation wins over the property
(`HomeRefreshFocusTest.kt` in `feature/home`).

A test that holds in the legacy mode only, such as a layout check that depends on the legacy text metrics, stays a
JUnit class with that annotation in the suite's file. It reuses the suite's fixture, as `rule()` returns the rule it
registers (`HomeMenuLastDockTileTest` in `HomeMenuUiTest.kt`):

```kotlin
private val fixture = HomeMenuUiTestFixture()

@get:Rule
val compose = fixture.compose
```

### Compose UI

A Compose test is a Robolectric suite whose tests get a fixture that registers the Compose rule. The fixture object
also holds the old class's fields and helpers. `createComposeRule()` and `createAndroidComposeRule<A>()` (also the `v2`
ones) work unchanged, and so do `waitUntil`, `mainClock` and `setContent`.

```kotlin
class SettingsHostTestContent :
    RobolectricTestSuiteContent({
        testFixture {
            object : JUnit4RulesContext() {
                val compose = rule(createAndroidComposeRule<ComponentActivity>())
                lateinit var viewModel: MoleculeSettingsViewModel

                fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        } asContextForEach {
            test("tv settings that do not open are told in the page and not by a toast") {
                compose.setContent { /* ... */ }
                ShadowToast.shownToastCount() shouldBe 0
            }
        }
    })
```

A `@Before` that ran inside the rules, such as one that waits for the activity under a Compose rule, becomes the
innermost link of the rule chain, so it still runs after every rule has started:

```kotlin
val rules: RuleChain =
    rule(
        RuleChain
            .outerRule(tvWithNineApps)
            .around(compose)
            .around(
                object : ExternalResource() {
                    override fun before() = waitForTheTiles()
                },
            ),
    )
```

`SettingsHostTest.kt` in `app` is the full example; `ClockActivityTest.kt` and `ConfigFocusStyleTest.kt` are the other
two. `MainActivityConfigTest.kt` has the rule chain, and `ConfigRepositoryTest.kt` in `core/config` has `init` and
`closeWith`.

### Coroutine tests

In a plain JVM suite, a `runTest` body moves into a test of a suite with `TestConfig.testScope(isEnabled = true)`.
TestBalloon then runs each test inside `runTest`, and `testScope` is that `TestScope`. Wrap the old body in
`with(testScope) { ... }`, so `advanceTimeBy`, `runCurrent`, `backgroundScope` and `launch` mean what they meant
before. Turbine's `flow.test { ... }` works unchanged inside it (`MinuteClockTest.kt` in `data/system`):

```kotlin
val MinuteClockTest by testSuite(testConfig = TestConfig.testScope(isEnabled = true)) {
    test("a valid time is emitted now and again at the next minute start") {
        with(testScope) {
            minuteClock(emptyFlow(), emptyFlow(), wallClock(valid), zone).test {
                awaitItem() shouldBe at(valid)
                advanceTimeBy(30_001)
                awaitItem() shouldBe at(valid + 30_000)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
```

In a Robolectric suite, keep `runTest { ... }` inside the test instead. With `TestConfig.testScope`, TestBalloon makes
the `TestScope` outside the Robolectric sandbox, and the first test fails with `LinkageError: loader constraint
violation ... kotlinx.coroutines.test.TestScope` (`OpenOnStartTest.kt` in `data/inputs`).

A test inside `asContextForEach` has the fixture as its receiver and the `Test.ExecutionScope` as its lambda
parameter, so `testScope` and `launch` are `it.testScope` and `it.launch` there.

The session's default configuration runs each test in a `TestScope` with a 60 s timeout, which JUnit did not have. A
test that takes longer fails with `UncompletedCoroutinesError: After waiting for 1m, the test body did not run to
completion`. A test that runs close to a minute, such as a pixel test that scrolls a row step by step, gets
`testConfig = TestConfig.testScope(isEnabled = true, timeout = 10.minutes)` (`FocusColourUiTest.kt` in
`feature/home`). `TestConfig.testScope(isEnabled = false)` on the test does not lift the limit.

## Assertions

| JUnit 4 | Kotest |
|---|---|
| `assertEquals(expected, actual)` | `actual shouldBe expected` |
| `assertNotEquals(a, b)` | `b shouldNotBe a` |
| `assertTrue(x)` / `assertFalse(x)` | `x.shouldBeTrue()` / `x.shouldBeFalse()` |
| `assertTrue("message", x)` | `withClue("message") { x.shouldBeTrue() }` |
| `assertNull(x)` / `assertNotNull(x)` | `x.shouldBeNull()` / `x.shouldNotBeNull()` |
| `assertTrue(list.contains(e))` | `list shouldContain e` |
| `assertTrue(a < b)` | `a shouldBeLessThan b` |
| `assertEquals(e, a, delta)` | `a shouldBe (e plusOrMinus delta)` |
| `assertSame(e, a)` | `a shouldBeSameInstanceAs e` |
| `assertArrayEquals(e, a)` | `a shouldBe e` (Kotest compares arrays by content) |
| `assertThrows(E::class.java) { ... }`, or `try { ...; fail() } catch (e: E) {}` | `shouldThrow<E> { ... }` |
| `assertTrue(x in a..b)` | `x shouldBeInRange a..b` |

A JUnit assert with three arguments is either `(message, expected, actual)` or `(expected, actual, delta)`: read the
types before you convert it.

A bare `shouldBeTrue()` fails with only `false should equal true`. Keep the old assert message as a `withClue`, or use
a matcher that names the values, such as `shouldContain` or `shouldBeLessThan`.

## Running and filtering

`./gradlew :app:testDebugUnitTest` runs the suites and the JUnit classes together. `--tests` selects suites and tests
by their path, with `↘` between the levels. The Robolectric suite's name is one of the levels:

```shell
./gradlew :app:testDebugUnitTest --tests 'dev.niltsiar.tvhome.ConfigFocusStyleTest↘rim and halo reach the focus look'
./gradlew :app:testDebugUnitTest --tests 'dev.niltsiar.tvhome.ClockActivityTest↘sdk 31↘a remote motion closes the clock'
./gradlew :app:testDebugUnitTest --tests 'dev.niltsiar.tvhome.SettingsHostTest↘*'
./gradlew :app:testDebugUnitTest --tests ';dev.niltsiar.tvhome.ClockActivityTest;*'
```

A pattern that starts with a character other than a letter uses that character as the separator. A JUnit class name
still selects that class alone. The configuration cache is reused across runs, filtered ones included.

## What to expect in the results

- The XML results put every suite test of a module into one file,
  `TEST-de.infix.testBalloon.framework.shared.internal.entryPoint.TestBalloonJUnit4.xml`, with that class as
  `classname` and the test name alone as `name`. The suite path shows in the console output only. Each test counts
  once, so the module's total does not change when a class moves.
- A module that applies the plugin but has no suite yet reports one extra test, `TestBalloonJUnit4`, in
  `TEST-Gradle-Test-Run-<module>-testDebugUnitTest.xml`. Apply the plugin in the same change as the module's first
  suite.
- While a module has Robolectric tests in both forms, the JUnit runner and TestBalloon each start their own Robolectric
  sandbox. In `app`, with three classes moved, `testDebugUnitTest` took about 5 s longer (median of 3 runs: 38 s against
  33 s).
- TestBalloon shows a `.` in a test name as `·`: `a lightness of 0.65` is reported as `a lightness of 0·65`.
- The Android Studio run gutter on a suite needs the TestBalloon plugin for IntelliJ. Status icons in the gutter do not
  show for Android tests, and "Rerun Failed Tests" is not supported.
