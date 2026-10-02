package com.jiang.vitality.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

@Composable
fun rememberAutoCollapseScrollState(onScroll: () -> Unit): ScrollState {
    val state = remember { ScrollState(0) }
    val currentOnScroll = rememberUpdatedState(onScroll)
    LaunchedEffect(state) {
        var previous = state.value
        snapshotFlow { state.value }.collect { current ->
            if (current != previous) {
                previous = current
                currentOnScroll.value()
            }
        }
    }
    return state
}

@Composable
fun rememberAutoCollapseLazyListState(onScroll: () -> Unit): LazyListState {
    val state = rememberLazyListState()
    val currentOnScroll = rememberUpdatedState(onScroll)
    LaunchedEffect(state) {
        var previous = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { current ->
            if (current != previous) {
                previous = current
                currentOnScroll.value()
            }
        }
    }
    return state
}
