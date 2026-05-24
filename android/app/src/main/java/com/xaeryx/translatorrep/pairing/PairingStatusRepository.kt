package com.xaeryx.translatorrep.pairing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * App-wide pairing status (Story 1.11, FR-4). Single instance held by
 * [com.xaeryx.translatorrep.TranslatorRepApplication]; MainActivity routes on [status].
 *
 * **Offline-first:** on [start] it emits the Room-mirrored pair immediately (no network), then
 * reconciles against a live `/pairs`-membership listener. The listener (Firestore, offline
 * cache on) also drives the immediate post-pair transition (it echoes the just-created local
 * `/pairs` doc) and survives a briefly-unreachable launch (serves the cached pair).
 *
 * The reconcile logic ([reconcile]) is pure suspend over the fake-able [PairDirectory] +
 * [PairingMirror] seams, so it's unit-tested without Firestore/Room (JUnit4-only toolchain).
 * [start]'s Flow wiring is a thin untested wrapper (matching the Story 1.8–1.10 split).
 */
class PairingStatusRepository(
    private val directory: PairDirectory,
    private val mirror: PairingMirror,
    private val scope: CoroutineScope,
) {

    private val _status = MutableStateFlow<PairingStatus>(PairingStatus.Unknown)
    val status: StateFlow<PairingStatus> = _status.asStateFlow()

    private var started = false

    /** Begin resolving pairing status for [myUid]. Idempotent — safe to call on every compose. */
    fun start(myUid: String) {
        if (started) return
        started = true
        scope.launch {
            // Offline-first: surface the mirrored pair immediately if we have one.
            mirror.read()?.let {
                _status.value = PairingStatus.Paired(it.pairId, it.partnerUid, it.partnerName)
            }
            // Then keep in sync with the live /pairs membership.
            directory.observePairFor(myUid).collect { remote ->
                _status.value = reconcile(myUid, remote)
            }
        }
    }

    /**
     * Fold a `/pairs` snapshot into [PairingStatus] + the local mirror. On a pair: on first
     * discovery, write our own `pairId` (partner-side consistency; best-effort so it can't
     * crash offline), preserve any cached partner name (default "Partner", FR-23), and update
     * the mirror. On `null`: clear the mirror and report [PairingStatus.Unpaired].
     */
    suspend fun reconcile(myUid: String, remote: RemotePair?): PairingStatus {
        if (remote == null) {
            mirror.clear()
            return PairingStatus.Unpaired
        }
        val partnerUid = remote.partnerOf(myUid)
        val cached = mirror.read()
        if (cached?.pairId != remote.pairId) {
            runCatching { directory.ensureOwnPairId(myUid, remote.pairId) }
        }
        val partnerName = cached?.takeIf { it.partnerUid == partnerUid }?.partnerName ?: DEFAULT_PARTNER_NAME
        mirror.save(MirroredPair(remote.pairId, partnerUid, partnerName))
        return PairingStatus.Paired(remote.pairId, partnerUid, partnerName)
    }

    private companion object {
        const val DEFAULT_PARTNER_NAME = "Partner"
    }
}
