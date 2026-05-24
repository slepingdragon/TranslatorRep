package com.xaeryx.translatorrep.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state of the own-pairing-code surface on the Paired-Empty home (Story 1.9). */
sealed interface PairingCodeUiState {
    /** Obtaining (reusing or allocating) the code — show a small progress affordance. */
    data object Loading : PairingCodeUiState

    /** The user's current 6-digit code, ready to display / copy / share. */
    data class Ready(val code: String) : PairingCodeUiState

    /** Allocation failed (e.g. offline, or the rare exhausted-collision-retry). */
    data class Error(val reason: String) : PairingCodeUiState
}

/** UI state of the partner-code input on the Paired-Empty home (Story 1.10). */
sealed interface PairingInputUiState {
    /** Idle / typing, no error. */
    data object Editing : PairingInputUiState

    /** Pair tapped; lookup + write in flight. */
    data object Submitting : PairingInputUiState

    /** Inline error below the input ("Code not found" / "Code expired" / "That's your own code"). */
    data class Error(val message: String) : PairingInputUiState

    /** Paired — `/pairs/{pairId}` created; the host navigates to the Paired home. */
    data class Paired(val pairId: String) : PairingInputUiState
}

/**
 * Drives the whole Paired-Empty home (Stories 1.9 + 1.10): the user's own code
 * ([codeState], via [PairingCodeAllocator]) and the partner-code input + pairing
 * ([inputState] / [enteredCode], via [PairingCoordinator]). Thin orchestration over those two
 * tested cores; `viewModelScope` + `StateFlow` adaptation only, so it has no unit test of its
 * own (matching the Story 1.8/1.9 split where the logic, not the framework-coupled wiring, is
 * tested). Constructed via [Factory] with the signed-in UID + Firestore-backed collaborators.
 */
class PairingViewModel(
    private val ownerUid: String,
    private val allocator: PairingCodeAllocator,
    private val coordinator: PairingCoordinator,
) : ViewModel() {

    // ── Own code (Story 1.9) ────────────────────────────────────────────────
    private val _codeState = MutableStateFlow<PairingCodeUiState>(PairingCodeUiState.Loading)
    val codeState: StateFlow<PairingCodeUiState> = _codeState.asStateFlow()

    // ── Partner-code input (Story 1.10) ─────────────────────────────────────
    private val _enteredCode = MutableStateFlow("")
    val enteredCode: StateFlow<String> = _enteredCode.asStateFlow()

    private val _inputState = MutableStateFlow<PairingInputUiState>(PairingInputUiState.Editing)
    val inputState: StateFlow<PairingInputUiState> = _inputState.asStateFlow()

    init {
        obtainCode()
    }

    /** Re-attempt obtaining the code after an [PairingCodeUiState.Error] (e.g. offline). */
    fun retry() {
        _codeState.value = PairingCodeUiState.Loading
        obtainCode()
    }

    private fun obtainCode() {
        viewModelScope.launch {
            _codeState.value = resultOf { allocator.obtain(ownerUid) }
        }
    }

    /** Long-press "Regenerate code": invalidate the current code and allocate a new one. */
    fun regenerate() {
        val current = (_codeState.value as? PairingCodeUiState.Ready)?.code ?: return
        viewModelScope.launch {
            _codeState.value = PairingCodeUiState.Loading
            _codeState.value = resultOf { allocator.regenerate(ownerUid, current) }
        }
    }

    /** Partner-code input change: keep digits only, max 6; clear any inline error on edit. */
    fun onCodeChange(raw: String) {
        _enteredCode.value = raw.filter(Char::isDigit).take(PAIRING_CODE_LENGTH)
        if (_inputState.value is PairingInputUiState.Error) {
            _inputState.value = PairingInputUiState.Editing
        }
    }

    /** Tap "Pair": validate length, then resolve + create the pair (Story 1.10, FR-3). */
    fun pair() {
        if (_enteredCode.value.length != PAIRING_CODE_LENGTH) return
        if (_inputState.value == PairingInputUiState.Submitting) return
        val code = _enteredCode.value
        viewModelScope.launch {
            _inputState.value = PairingInputUiState.Submitting
            _inputState.value = attemptPair(code)
        }
    }

    @Suppress("TooGenericExceptionCaught") // Firestore throws varied types; map to a retry error.
    private suspend fun attemptPair(code: String): PairingInputUiState =
        try {
            when (val result = coordinator.pair(ownerUid, code)) {
                is PairResult.Success -> PairingInputUiState.Paired(result.pairId)
                PairResult.NotFound -> PairingInputUiState.Error(ERR_NOT_FOUND)
                PairResult.OwnCode -> PairingInputUiState.Error(ERR_OWN_CODE)
                PairResult.Expired -> PairingInputUiState.Error(ERR_EXPIRED)
            }
        } catch (e: Exception) {
            PairingInputUiState.Error(ERR_GENERIC)
        }

    @Suppress("TooGenericExceptionCaught") // Firestore throws varied types; all map to Error.
    private suspend fun resultOf(block: suspend () -> String): PairingCodeUiState =
        try {
            PairingCodeUiState.Ready(block())
        } catch (e: Exception) {
            PairingCodeUiState.Error(e.javaClass.simpleName)
        }

    /** Supplies the runtime [ownerUid] + collaborators to `viewModel(factory = …)`. */
    class Factory(
        private val ownerUid: String,
        private val allocator: PairingCodeAllocator,
        private val coordinator: PairingCoordinator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PairingViewModel(ownerUid, allocator, coordinator) as T
    }

    private companion object {
        const val PAIRING_CODE_LENGTH = 6
        const val ERR_NOT_FOUND = "Code not found"
        const val ERR_OWN_CODE = "That's your own code"
        const val ERR_EXPIRED = "Code expired"
        const val ERR_GENERIC = "Couldn't pair. Check your connection and try again."
    }
}
