# ViewModel UI State and Flow Collection Pitfalls

This repository demonstrates common pitfalls when collecting Kotlin Flows within an Android ViewModel, specifically concerning UI lifecycle awareness. It highlights issues that can arise when flow collection is not properly managed, leading to unnecessary background processing and resource consumption.

The concepts and solutions presented are inspired by and align with the best practices discussed in the Medium article: [How to collect flows in UI, the right way](https://medium.com/proandroiddev/how-to-load-data-kotlin-898f9add9c6f).

## The Problem: Unmanaged Flow Collection

A common mistake is to launch a coroutine within `viewModelScope` to collect a flow and update a `StateFlow` or `LiveData`. While `viewModelScope` is tied to the ViewModel's lifecycle, it doesn't automatically stop collection when the UI is no longer visible (e.g., app in the background). This can lead to:

*   **Wasted Resources:** The flow continues to be collected, data processed, and state updated even when the UI isn't observing it.
*   **Potential Bugs:** Unexpected behavior might occur if background processing affects other parts of the application or if data is updated when it shouldn't be.

## Demonstrating the Pitfall

In this project, `UiChangesViewModel.kt` showcases two approaches:

1.  **`wrongWay`:**
    This `StateFlow` is populated by launching a coroutine in `viewModelScope` to collect an underlying `numbers` flow.

    ```kotlin
    // Inside UiChangesViewModel.kt
    private val _wrongWay = MutableStateFlow(0)
    val wrongWay = _wrongWay.onStart {
        viewModelScope.launch { // Problematic launch
            currentNumber.collect()
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(2000L), // Note: onStart still launches independently
        0
    )

    private val currentNumber = numbers
        .distinctUntilChanged()
        .onEach { number ->
            Log.i("UiChangesViewModel", "Emitted new number $number") // This will keep logging
            _wrongWay.update { number }
        }
    ```
    If you run the app, put it in the background, and observe Logcat for "UiChangesViewModel", you'll see that `currentNumber` (and thus `_wrongWay`) continues to emit and process new numbers. This is because the `viewModelScope.launch` block in `onStart` is not tied to UI visibility.

2.  **`correctWay`:**
    This `StateFlow` uses the `stateIn` operator with `SharingStarted.WhileSubscribed()`. This operator is lifecycle-aware. The upstream flow (`numbers`) will only be collected when there's at least one active subscriber (collector) from the UI. When the UI goes to the background and stops collecting, the upstream flow collection will also stop after the configured timeout (`2000L` in this case).

    ```kotlin
    // Inside UiChangesViewModel.kt
    val correctWay = numbers
        .onEach { Log.i("UiChangesViewModel", "CorrectWay: Emitted new number $it") }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(2000L), // Stops when UI is not observing
            0
        )
    ```
    When observing logs for `CorrectWay: Emitted new number`, you'll notice that emissions stop shortly after the app is backgrounded and resume when the app is brought back to the foreground.

## Running the Sample

1.  Clone the repository.
2.  Open the project in Android Studio or IntelliJ IDEA.
3.  Run the app on an emulator or physical device.
4.  Open Logcat and filter by the tag `UiChangesViewModel`.
5.  Observe the log messages:
    *   You will see logs like `"Emitted new number X"` from the `currentNumber` flow (related to `wrongWay`).
    *   You will see logs like `"CorrectWay: Emitted new number Y"` from the `correctWay` flow.
6.  Put the app in the background (e.g., by pressing the home button).
7.  Notice that the logs for `"Emitted new number X"` (from `wrongWay`) continue, while the logs for `"CorrectWay: Emitted new number Y"` stop after the `SharingStarted.WhileSubscribed` timeout (2 seconds in this example).
8.  Bring the app back to the foreground. The logs for `"CorrectWay: Emitted new number Y"` will resume.

## Key Takeaway

Always use lifecycle-aware collection mechanisms like `stateIn` with `SharingStarted.WhileSubscribed()` or `SharedFlow` with appropriate replay/buffer configurations when exposing data from ViewModels as Flows to the UI. Avoid launching long-lived collection coroutines directly in `viewModelScope` without proper cancellation tied to UI visibility.

For Jetpack Compose UIs, collecting with `collectAsStateWithLifecycle()` (as used in `MainActivity.kt`) further ensures that collection is tied to the Composable's lifecycle on the UI side.
