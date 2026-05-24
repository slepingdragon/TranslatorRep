package com.xaeryx.translatorrep

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Story 1.6d AC-3 scaffold — proves the Compose UI instrumented-test pipeline
 * compiles + has the right test dependencies wired (createAndroidComposeRule
 * + JUnit4 test runner + Espresso transitive). Single sanity assertion against
 * the Story-1.1 hello-world content.
 *
 * **NOT RUN IN CI.** Instrumented tests need an emulator runner (Firebase Test
 * Lab or Gradle Managed Devices); both have non-trivial setup overhead + cost
 * trade-offs not justified for v1 baseline. Activation is deferred until
 * there's a clear ROI (e.g., a flaky Compose UI bug that keeps slipping
 * through unit tests + screenshot tests). When activated, see Story 1-6e or
 * a future 1-6f for CI integration.
 *
 * **To run locally:** plug in a phone OR start an AVD, then
 * `./gradlew :app:connectedDebugAndroidTest`. Will not pass unless an emulator
 * or physical device is connected.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun helloWorld_displays_TranslatorRep_title() {
        composeRule.onNodeWithText("TranslatorRep").assertIsDisplayed()
    }
}
