package com.f776.viewmodeluistate

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class UiChangesViewModel : ViewModel() {

    private val numbers = flow {
        while (true) {
            delay(500L)
            emit(Random.nextInt() * Random.nextInt())
        }
    }

    val correctWay = numbers
        .distinctUntilChanged()
        .onEach { Log.i("UiChangesViewModel", "Emitted new number $it") }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(2000L),
            0
        )

    private val _wrongWay = MutableStateFlow(0)
    val wrongWay = _wrongWay.onStart {
        viewModelScope.launch {
            currentNumber.collect()
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(2000L),
        0
    )

    private val currentNumber = numbers
        .distinctUntilChanged()
        .onEach { number ->
            Log.i("UiChangesViewModel", "Emitted new number $number")
            _wrongWay.update { number }
        }
}