package com.xaeryx.translatorrep.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.xaeryx.translatorrep.logging.AllowedLogKey
import com.xaeryx.translatorrep.logging.SafeLog
import kotlinx.coroutines.tasks.await

/**
 * One-shot Firebase smoke test for Story 1.4 AC-5. Exercises three behaviors
 * end-to-end on a real device against the real Firebase project:
 *
 * 1. **Anonymous sign-in** — proves Auth is configured + reachable. Logs
 *    [AllowedLogKey.AUTH_UID] = first [UID_LOG_PREFIX_LENGTH] chars of the
 *    uid (never the full uid — privacy convention per architecture §14).
 * 2. **Own-uid Firestore write** to `/users/{uid}` — proves Firestore is
 *    reachable AND the security rule `allow write if request.auth.uid == uid`
 *    permits the owning user.
 * 3. **Forbidden-uid Firestore write** to `/users/smoke-other-{4chars}` —
 *    proves the security rule REJECTS writes by anyone other than the owner.
 *    The expected outcome is a Firestore exception (PERMISSION_DENIED);
 *    if the write succeeds, that's a rules regression and the SafeLog event
 *    fires with `unexpectedly-allowed` so it shows up loudly.
 *
 * **Triggering:** debug-only via intent extra (see [MainActivity.onCreate]).
 * Use adb:
 *   `adb shell am start -n com.xaeryx.translatorrep/.MainActivity --es firebase-smoke true`
 *
 * Not for production code paths — Stories 1.8-1.13 implement the real
 * pairing-arc sign-in + Firestore writes with proper UI + error handling.
 *
 * @see [docs/runbooks/firebase-setup-android.md] §8 for the expected Logcat output.
 */
object FirebaseSmokeTest {

    private const val UID_LOG_PREFIX_LENGTH = 4
    private const val FORBIDDEN_DOC_PREFIX = "smoke-other-"

    /**
     * Runs the three smoke checks sequentially. Each failure short-circuits
     * subsequent steps (e.g., if sign-in fails, no point attempting writes).
     * All outcomes are SafeLog'd; the function never throws.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun runOnce() {
        // ── 1. Anonymous sign-in ──────────────────────────────────────────
        val uid: String = try {
            val authResult = FirebaseAuth.getInstance().signInAnonymously().await()
            authResult.user?.uid ?: run {
                SafeLog.event(AllowedLogKey.AUTH_UID, "failed:no-user")
                return
            }
        } catch (e: Exception) {
            SafeLog.event(AllowedLogKey.AUTH_UID, "failed:${e.javaClass.simpleName}")
            return
        }
        // Privacy: log first N chars only, never the full UID.
        SafeLog.event(AllowedLogKey.AUTH_UID, uid.take(UID_LOG_PREFIX_LENGTH))

        val firestore = FirebaseFirestore.getInstance()

        // ── 2. Own write — should succeed per security rules ──────────────
        try {
            firestore.collection("users").document(uid)
                .set(mapOf("smokeTest" to true, "ts" to FieldValue.serverTimestamp()))
                .await()
            SafeLog.event(AllowedLogKey.SMOKE_USERS_WRITE, "success")
        } catch (e: Exception) {
            SafeLog.event(AllowedLogKey.SMOKE_USERS_WRITE, "failed:${e.javaClass.simpleName}")
            return
        }

        // ── 3. Forbidden write — should be rejected with PERMISSION_DENIED ─
        // Use a deterministic non-owned target uid so the smoke test doesn't
        // depend on which other docs exist in the database.
        val forbiddenTargetUid = "$FORBIDDEN_DOC_PREFIX${uid.take(UID_LOG_PREFIX_LENGTH)}"
        try {
            firestore.collection("users").document(forbiddenTargetUid)
                .set(mapOf("smokeTest" to true))
                .await()
            // If we reach here, rules are NOT enforcing — regression alarm.
            SafeLog.event(AllowedLogKey.SMOKE_FORBIDDEN_WRITE, "unexpectedly-allowed")
        } catch (e: Exception) {
            // Expected path — Firestore returns PERMISSION_DENIED.
            SafeLog.event(AllowedLogKey.SMOKE_FORBIDDEN_WRITE, "denied")
        }
    }
}
