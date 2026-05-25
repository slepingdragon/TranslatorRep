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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

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

    override fun connect(callType: CallType, peerUid: String): Flow<RoomState> = flow {
        when (val token = tokenFetcher.fetchToken(callType, peerUid)) {
            is TokenResult.Failure -> {
                // Surface WHY the token fetch failed (ERR_* from the proxy, or a client-side
                // ERR_TOKEN_*). Without this the call just silently "ends" and the cause is
                // only inferable from the ABSENCE of LiveKit logs. Logged as error_code so it
                // lands in Logcat (debug) / Crashlytics (release).
                SafeLog.event(AllowedLogKey.ERROR_CODE, "token_fetch_${token.errorCode}")
                emit(RoomState.ENDED) // couldn't get a token → no call
            }
            is TokenResult.Success -> emitCall(token)
        }
    }.onCompletion {
        // Flow cancelled (user left the call) or completed → tear down the room.
        room?.disconnect()
        room = null
        routeState.value = AudioRoute.EARPIECE
    }

    /** Connect + publish mic, then hold [RoomState.ACTIVE] until the flow is cancelled. */
    @Suppress("TooGenericExceptionCaught") // LiveKit connect throws varied types; map to ENDED.
    private suspend fun FlowCollector<RoomState>.emitCall(token: TokenResult.Success) {
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
        if (connected) {
            emit(RoomState.ACTIVE)
            // Stay active until the PEER leaves or the room drops, then end for us too (Story 2.8
            // peer-left). Pressing End instead cancels this flow (collector disposed) → onCompletion
            // tears the room down. In a 1:1 call, any ParticipantDisconnected is the partner hanging
            // up. (Rich lifecycle — network-drop reconnect, leave-and-rejoin — is Epic 7.)
            activeRoom.awaitPeerLeftOrDisconnect()
            emit(RoomState.ENDED)
        } else {
            emit(RoomState.ENDED)
        }
    }

    /**
     * Suspend until the partner disconnects or the room itself drops. `room.events` is a LiveKit
     * [io.livekit.android.events.EventListenable] (not a kotlinx Flow), so we collect it in a
     * child coroutine and resolve a [CompletableDeferred] on the first terminal event, then cancel
     * the collector. Cancelling the caller (user pressed End) cancels this scope cleanly.
     */
    private suspend fun Room.awaitPeerLeftOrDisconnect() = coroutineScope {
        val terminal = CompletableDeferred<Unit>()
        val collector = launch {
            events.collect { event ->
                if (event is RoomEvent.ParticipantDisconnected || event is RoomEvent.Disconnected) {
                    terminal.complete(Unit)
                }
            }
        }
        terminal.await()
        collector.cancel()
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
}
