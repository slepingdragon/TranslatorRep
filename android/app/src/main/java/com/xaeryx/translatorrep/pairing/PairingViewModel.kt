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

/**
 * Drives the own-pairing-code panel (Story 1.9, FR-2). Thin orchestration over
 * [PairingCodeAllocator]: obtain the code on creation, regenerate on demand. The testable
 * logic lives in the allocator (pure suspend + fake-able [CodeStore]); this class only adapts
 * it to `viewModelScope` + a [StateFlow], so it has no unit test of its own (matching the
 * Story 1.8 split where the repository, not the framework-coupled wiring, is tested).
 *
 * Constructed via [Factory] with the signed-in UID (from
 * `TranslatorRepApplication.authRepository`) and a Firestore-backed allocator.
 */
class PairingViewModel(
    private val ownerUid: String,
    private val allocator: PairingCodeAllocator,
) : ViewModel() {

    private val _codeState = MutableStateFlow<PairingCodeUiState>(PairingCodeUiState.Loading)
    val codeState: StateFlow<PairingCodeUiState> = _codeState.asStateFlow()

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

    @Suppress("TooGenericExceptionCaught") // Firestore throws varied types; all map to Error.
    private suspend fun resultOf(block: suspend () -> String): PairingCodeUiState =
        try {
            PairingCodeUiState.Ready(block())
        } catch (e: Exception) {
            PairingCodeUiState.Error(e.javaClass.simpleName)
        }

    /** Supplies the runtime [ownerUid] + [allocator] to `viewModel(factory = …)`. */
    class Factory(
        private val ownerUid: String,
        private val allocator: PairingCodeAllocator,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PairingViewModel(ownerUid, allocator) as T
    }
}
