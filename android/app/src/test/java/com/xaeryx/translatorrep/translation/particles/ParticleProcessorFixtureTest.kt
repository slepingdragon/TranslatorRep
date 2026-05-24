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
 * **Story 3.2 scope:** only the `loh` rule is fully implemented + tested. The
 * test method [allLohFixturesPassPreProcessAndPostProcessIdempotent] runs every
 * `loh/case_NNN/` fixture; sub-letter follow-ups (Story 3.2b) will add tests
 * for the remaining rule categories with metadata-driven parameterization.
 *
 * **Test strategy** — see `/shared/particle-rules-fixtures/README.md`:
 * - [preProcess] test: assert `preProcess(source) == expected_processed`.
 *   Validates that the particle was correctly tagged.
 * - [postProcess] idempotency test: assert
 *   `postProcess(expected_target, source) == expected_target`. Validates that
 *   postProcess doesn't double-inject when the equivalent is already present
 *   (the realistic NMT-output case where the model produced the equivalent
 *   naturally). The harder "inject-from-naive-NMT" test is a follow-up that
 *   needs a `raw_target.txt` field added to fixtures.
 *
 * **Why no metadata.json parsing in this PR:** would require either `org.json`
 * (Android's mock SDK stubs it for unit tests, so it'd fail at runtime) or a
 * new test-only dependency. All current `loh/case_NNN/` fixtures use
 * `id-ID` → `en-US` with `expected_particles: ["loh"]`, so hardcoding those
 * test parameters is honest for the current scope. Story 3.2b switches to
 * metadata-driven parameterization when rules across multiple language pairs
 * + particles need varied expectations.
 */
class ParticleProcessorFixtureTest {

    @Test
    fun `all loh fixtures pass preProcess and postProcess idempotency`() {
        val lohDir = File(fixtureRoot, "particles/loh")
        assertTrue(
            "loh fixture dir not found at ${lohDir.absolutePath} — check `repo.root` system property + shared/particle-rules-fixtures/ presence",
            lohDir.isDirectory,
        )
        val cases = lohDir.listFiles { f -> f.isDirectory && f.name.startsWith("case_") }
            ?.sortedBy { it.name }
            ?: emptyList()
        assertTrue(
            "Expected ≥$MIN_CASES_PER_RULE fixture cases per Story 3.2 AC; found ${cases.size} in ${lohDir.absolutePath}",
            cases.size >= MIN_CASES_PER_RULE,
        )

        for (caseDir in cases) {
            runFixture(caseDir)
        }
    }

    private fun runFixture(caseDir: File) {
        val source = File(caseDir, "source.txt").readText().trimEnd('\n', '\r')
        val expectedProcessed = File(caseDir, "expected_processed.txt").readText().trimEnd('\n', '\r')
        val expectedTarget = File(caseDir, "expected_target.txt").readText().trimEnd('\n', '\r')

        // Pass 1: preProcess source produces expected tagged text.
        val processed = ParticleProcessor.preProcess(source, LOH_SOURCE_LANG, LOH_TARGET_LANG)
        assertEquals(
            "[${caseDir.name}] preProcess output mismatch",
            expectedProcessed,
            processed.text,
        )
        assertEquals(
            "[${caseDir.name}] preProcess detected particles mismatch",
            LOH_EXPECTED_PARTICLES,
            processed.particles,
        )

        // Pass 2: postProcess idempotency — passing expected_target back in
        // should leave it unchanged + detect every expected particle.
        val postProcessed = ParticleProcessor.postProcess(
            rawTarget = expectedTarget,
            originalSource = source,
            sourceLang = LOH_SOURCE_LANG,
            targetLang = LOH_TARGET_LANG,
        )
        assertEquals(
            "[${caseDir.name}] postProcess idempotency violated — passing expected_target back through postProcess changed it",
            expectedTarget,
            postProcessed.text,
        )
        for (particle in LOH_EXPECTED_PARTICLES) {
            assertTrue(
                "[${caseDir.name}] postProcess.particlesPreserved missing expected '$particle' (got ${postProcessed.particlesPreserved})",
                postProcessed.particlesPreserved.contains(particle),
            )
        }
    }

    companion object {
        /**
         * Project (Gradle module) root passed via `repo.root` system property from
         * `app/build.gradle.kts`. Resolves to repo root (parent of `android/`).
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
        private const val LOH_SOURCE_LANG = "id-ID"
        private const val LOH_TARGET_LANG = "en-US"
        private val LOH_EXPECTED_PARTICLES = listOf("loh")
    }
}
