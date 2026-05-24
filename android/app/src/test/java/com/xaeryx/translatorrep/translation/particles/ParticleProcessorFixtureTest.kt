package com.xaeryx.translatorrep.translation.particles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Golden-file fixture tests for [ParticleProcessor].
 *
 * Loads fixtures from `/shared/particle-rules-fixtures/` (located via the
 * `repo.root` system property set by `app/build.gradle.kts` test config). Each
 * fixture is `<category>/<rule>/case_NNN/` containing `source.txt`,
 * `expected_processed.txt`, `expected_target.txt`, `metadata.json`.
 *
 * **Story 3.2b Phase 1 update:** the previous loh-specific test was generalized
 * to iterate every `particles/<rule_name>/` directory and run the same
 * preProcess + postProcess idempotency assertions per fixture. The rule name
 * is derived from the directory name and doubles as the expected particle
 * token (since all TQ-1 rules tag with their own name).
 *
 * **Test strategy** — see `/shared/particle-rules-fixtures/README.md`:
 * - [preProcess] test: assert `preProcess(source) == expected_processed` AND
 *   that the detected particle list equals `[ruleName]`.
 * - [postProcess] idempotency test: assert
 *   `postProcess(expected_target, source) == expected_target`. Validates that
 *   postProcess doesn't double-inject when the equivalent is already present
 *   (the realistic NMT-output case where the model produced the equivalent
 *   naturally). The harder "inject-from-naive-NMT" test is a follow-up that
 *   needs `raw_target.txt` added to fixtures.
 *
 * **Why language-pair is still hardcoded:** all current `particles/<rule>/`
 * fixtures use `id-ID → en-US`. When Story 3.2c adds Sundanese-source rules
 * (`su-ID`) or any other language pair, this test switches to metadata-driven
 * parameterization (and pulls in a JSON parser — see Story 3.2 dev notes for
 * why that's deferred).
 */
class ParticleProcessorFixtureTest {

    @Test
    fun `all particle fixtures pass preProcess and postProcess idempotency`() {
        val particlesDir = File(fixtureRoot, "particles")
        assertTrue(
            "particles/ dir not found at ${particlesDir.absolutePath}",
            particlesDir.isDirectory,
        )
        val ruleDirs = particlesDir.listFiles { f -> f.isDirectory }
            ?.sortedBy { it.name }
            ?: emptyList()
        assertTrue(
            "Expected at least one rule directory under particles/; found none",
            ruleDirs.isNotEmpty(),
        )

        for (ruleDir in ruleDirs) {
            val ruleName = ruleDir.name
            val cases = ruleDir.listFiles { f -> f.isDirectory && f.name.startsWith("case_") }
                ?.sortedBy { it.name }
                ?: emptyList()
            assertTrue(
                "Rule '$ruleName' needs ≥$MIN_CASES_PER_RULE fixture cases per Story 3.2 AC; found ${cases.size}",
                cases.size >= MIN_CASES_PER_RULE,
            )
            for (caseDir in cases) {
                runFixture(caseDir = caseDir, ruleName = ruleName)
            }
        }
    }

    private fun runFixture(caseDir: File, ruleName: String) {
        val source = File(caseDir, "source.txt").readText().trimEnd('\n', '\r')
        val expectedProcessed = File(caseDir, "expected_processed.txt").readText().trimEnd('\n', '\r')
        val expectedTarget = File(caseDir, "expected_target.txt").readText().trimEnd('\n', '\r')

        // Pass 1: preProcess source produces expected tagged text.
        val processed = ParticleProcessor.preProcess(source, SOURCE_LANG, TARGET_LANG)
        assertEquals(
            "[${ruleName}/${caseDir.name}] preProcess output mismatch",
            expectedProcessed,
            processed.text,
        )
        assertEquals(
            "[${ruleName}/${caseDir.name}] preProcess detected particles mismatch",
            listOf(ruleName),
            processed.particles,
        )

        // Pass 2: postProcess idempotency — passing expected_target back in
        // should leave it unchanged + detect the expected particle.
        val postProcessed = ParticleProcessor.postProcess(
            rawTarget = expectedTarget,
            originalSource = source,
            sourceLang = SOURCE_LANG,
            targetLang = TARGET_LANG,
        )
        assertEquals(
            "[${ruleName}/${caseDir.name}] postProcess idempotency violated — passing expected_target back through postProcess changed it",
            expectedTarget,
            postProcessed.text,
        )
        assertTrue(
            "[${ruleName}/${caseDir.name}] postProcess.particlesPreserved missing expected '$ruleName' (got ${postProcessed.particlesPreserved})",
            postProcessed.particlesPreserved.contains(ruleName),
        )
    }

    companion object {
        /**
         * Repo root passed via `repo.root` system property from `app/build.gradle.kts`.
         */
        private val fixtureRoot: File by lazy {
            val rootProp = System.getProperty("repo.root")
                ?: error(
                    "System property `repo.root` not set. Check app/build.gradle.kts " +
                        "tasks.withType<Test> block — it must set systemProperty(\"repo.root\", ...).",
                )
            val candidate = File(rootProp, "shared/particle-rules-fixtures")
            check(candidate.isDirectory) {
                "Expected fixture dir at ${candidate.absolutePath} — repo layout drift?"
            }
            candidate
        }

        private const val MIN_CASES_PER_RULE = 3
        private const val SOURCE_LANG = "id-ID"
        private const val TARGET_LANG = "en-US"
    }
}
