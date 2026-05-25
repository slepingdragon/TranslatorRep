package com.xaeryx.translatorrep.call.livekit

import android.content.Context
import com.twilio.audioswitch.AudioDevice
import com.xaeryx.translatorrep.call.AudioRoute
import com.xaeryx.translatorrep.call.CallType
import com.xaeryx.translatorrep.call.callSession.RoomManager
import com.xaeryx.translatorrep.call.callSession.RoomState
import com.xaeryx.translatorrep.logging.AllowedLogKey
import com.xaeryx.translatorrep.logging.SafeLog
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The only class that talks to the LiveKit Android SDK (architecture Patterns §13). Owned by
 * [com.xaeryx.translatorrep.call.callSession.CallSession]; the UI never references it.
 *
 * Story 2.3 wires the real connection: fetch a JWT from the auth-proxy
 * ([LiveKitTokenFetcher]) → `room.connect(wsUrl, jwt)` → publish the local mic → stay in the
 * call until the collecting flow is cancelled (user ends), which disconnects the room. (E2EE
 * Insertable Streams is Epic 5; per-call media is DTLS-SRTP until then. Rich room-event
 * handling — peer-left, network drop, leave-and-rejoin — arrives with Epic 7.)
 */
class LiveKitRoomManager(
    private val appContext: Context,
    private val tokenFetcher: LiveKitTokenFetcher = LiveKitTokenFetcher(),
) : RoomManager {

    @Volatile
    private var room: Room? = null

    // LiveKit's audio handler — manages focus + output routing (earpiece/speaker/BT/wired,
    // auto-select at start + mid-call fallback). We pass our OWN instance (Story 2.9) instead of
    // letting LiveKit create an internal one, so the In-Call routing toggle can drive
    // selectDevice() and we can reflect the active device into [audioRoute].
    private val audioSwitch = AudioSwitchHandler(appContext)
    private val routeState = MutableStateFlow(AudioRoute.EARPIECE)
    override val audioRoute: Flow<AudioRoute> = routeState

    init {
        // Default routing for a private 1:1 call: prefer EARPIECE over speaker (like a normal
        // phone call), while still auto-selecting Bluetooth/wired when present. The user opts in
        // to speaker with an explicit tap. LiveKit's stock list puts Speakerphone before Earpiece,
        // which starts every call on loudspeaker — not what we want for a private call.
        audioSwitch.preferredDeviceList = listOf(
            AudioDevice.BluetoothHeadset::class.java,
            AudioDevice.WiredHeadset::class.java,
            AudioDevice.Earpiece::class.java,
            AudioDevice.Speakerphone::class.java,
        )
        // Reflect the handler's selected device: covers auto-select at call start and the
        // fallback to earpiece when a BT/wired device disconnects mid-call.
        audioSwitch.registerAudioDeviceChangeListener { _, selected ->
            routeState.value = selected.toAudioRoute()
        }
    }

    override fun connect(callType: CallType, peerUid: String): Flow<RoomState> = channelFlow {
        when (val token = tokenFetcher.fetchToken(callType, peerUid)) {
            is TokenResult.Failure -> {
                // Surface WHY the token fetch failed (ERR_* from the proxy, or a client-side
                // ERR_TOKEN_*) — otherwise a failed call just silently "ends" and the cause is
                // only inferable from the ABSENCE of LiveKit logs.
                SafeLog.event(AllowedLogKey.ERROR_CODE, "token_fetch_${token.errorCode}")
                send(RoomState.ENDED)
            }
            is TokenResult.Success -> runCall(token)
        }
    }.onCompletion {
        // Flow cancelled (user left the call) or completed → tear down the room.
        room?.disconnect()
        room = null
        routeState.value = AudioRoute.EARPIECE
    }

    /**
     * Connect + publish mic, then run the call's state machine until it ends:
     *  - [RoomState.WAITING_FOR_PARTNER] ("Calling…") while we're connected but the partner hasn't
     *    joined yet; [RoomState.ACTIVE] the moment they do (or immediately if they were already
     *    there — i.e. we're the one accepting).
     *  - [RoomState.ENDED] when the partner leaves / the room drops, OR if nobody joins within
     *    [RING_TIMEOUT_MS] (no answer / declined — so the caller never sits in an empty room).
     * Uses channelFlow so the event-collector + ring-timeout coroutines can [send] concurrently.
     */
    @Suppress("TooGenericExceptionCaught") // LiveKit connect throws varied types; map to ENDED.
    private suspend fun ProducerScope<RoomState>.runCall(token: TokenResult.Success) {
        val activeRoom = LiveKit.create(
            appContext,
            overrides = LiveKitOverrides(audioOptions = AudioOptions(audioHandler = audioSwitch)),
        )
        room = activeRoom
        val connected = try {
            activeRoom.connect(token.livekitWsUrl, token.livekitJwt)
            activeRoom.localParticipant.setMicrophoneEnabled(true)
            true
        } catch (e: CancellationException) {
            throw e // normal teardown — let it propagate to onCompletion
        } catch (e: Exception) {
            false
        }
        if (!connected) {
            send(RoomState.ENDED)
            return
        }

        // ACTIVE only once the PARTNER is actually present. Already here → we're accepting (active
        // now); otherwise we're calling → WAITING_FOR_PARTNER ("Calling…") until they join.
        val peerPresent = activeRoom.remoteParticipants.isNotEmpty()
        val peerJoined = CompletableDeferred<Unit>()
        if (peerPresent) peerJoined.complete(Unit)
        val terminal = CompletableDeferred<Unit>()

        send(if (peerPresent) RoomState.ACTIVE else RoomState.WAITING_FOR_PARTNER)

        val eventCollector = launch {
            activeRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected -> peerJoined.complete(Unit)
                    is RoomEvent.ParticipantDisconnected, is RoomEvent.Disconnected ->
                        terminal.complete(Unit)
                    else -> Unit
                }
            }
        }
        val activator = launch {
            peerJoined.await()
            if (!peerPresent) send(RoomState.ACTIVE)
        }
        val ringTimeout = launch {
            if (withTimeoutOrNull(RING_TIMEOUT_MS) { peerJoined.await() } == null) {
                terminal.complete(Unit) // nobody answered
            }
        }

        terminal.await()
        eventCollector.cancel()
        activator.cancel()
        ringTimeout.cancel()
        send(RoomState.ENDED)
    }

    override suspend fun disconnect() {
        room?.disconnect()
        room = null
    }

    /** Flip the local mic track (Story 2.7). No-op if the room isn't up yet. */
    override suspend fun setMicrophoneEnabled(enabled: Boolean) {
        room?.localParticipant?.setMicrophoneEnabled(enabled)
    }

    /**
     * Cycle to the next available output route (Story 2.9). The handler populates
     * [AudioSwitchHandler.availableAudioDevices] once the room starts it; no-op before then.
     * [AudioSwitchHandler.selectDevice] is sticky and survives device hot-plug.
     */
    override fun cycleAudioRoute() {
        val devices = audioSwitch.availableAudioDevices
        if (devices.isEmpty()) return
        val nextIndex = (devices.indexOf(audioSwitch.selectedAudioDevice) + 1) % devices.size
        audioSwitch.selectDevice(devices[nextIndex])
    }

    private fun AudioDevice?.toAudioRoute(): AudioRoute = when (this) {
        is AudioDevice.Speakerphone -> AudioRoute.SPEAKER
        is AudioDevice.BluetoothHeadset -> AudioRoute.BLUETOOTH
        is AudioDevice.WiredHeadset -> AudioRoute.WIRED_HEADSET
        is AudioDevice.Earpiece, null -> AudioRoute.EARPIECE
    }

    private companion object {
        /** How long the caller rings before giving up (no answer / declined). Story 2.5: ~30s. */
        const val RING_TIMEOUT_MS = 30_000L
    }
}
