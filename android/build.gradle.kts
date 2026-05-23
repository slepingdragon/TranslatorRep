// Top-level build file. Common configuration applies via plugin DSL in
// app/build.gradle.kts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
}

// Story 1.5 — detekt is applied explicitly inside `:app/build.gradle.kts` (see
// the `detekt { ... }` and `tasks.withType<Detekt>()` blocks there). The
// project is currently single-module, so a root `subprojects { apply(plugin = ...) }`
// propagation block would be a no-op today. When a second Gradle module is
// added (e.g., a `:detekt-rules` custom-rule module per Story 1.5 Task 7.3
// Option B fallback, a `:shared-kmp` module per architecture §12, or any
// LiveKit fork), THAT module's build script MUST add `alias(libs.plugins.detekt)`
// (and its own `detekt { config.setFrom(...) }` block pointing at the shared
// `$rootDir/detekt-config.yml`) — otherwise the SafeLog ForbiddenImport gate
// develops a silent backdoor in the new module.
