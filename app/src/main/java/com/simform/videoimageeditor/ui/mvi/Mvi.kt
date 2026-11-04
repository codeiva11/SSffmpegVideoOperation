package com.simform.videoimageeditor.ui.mvi

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * MVI base, per the migration guide: the ViewModel exposes a single immutable
 * state class that is observed, and receives every UI interaction as a single
 * sealed event type. The whole UI can be recreated from the state alone.
 */
abstract class MviViewModel<S, E>(initialState: S) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    protected fun setState(reduce: S.() -> S) {
        _state.update { it.reduce() }
    }

    abstract fun onEvent(event: E)
}
