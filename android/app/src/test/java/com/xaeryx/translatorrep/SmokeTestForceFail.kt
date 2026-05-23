package com.xaeryx.translatorrep

import org.junit.Test
import kotlin.test.assertEquals

/**
 * SMOKE TEST — Story 1.6 AC-5 #2: this test must fail on the
 * smoke/1-6-test-break branch to validate that android-ci.yml correctly
 * catches a deliberately broken unit test. This file is throwaway — the PR
 * containing it is closed without merge.
 */
class SmokeTestForceFail {
    @Test
    fun smokeTest_intentional_failure_for_AC5_validation() {
        assertEquals(1, 2, "Story 1.6 AC-5 smoke: this assertion must fail")
    }
}
