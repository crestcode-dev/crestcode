package com.crestcode.trace

/**
 * Immutable data representations of performance trace events.
 * 
 * CRITICAL SAFETY REQUIREMENT:
 * Never retain references to Activity, View, Context, or Compose state.
 * Only primitive values and simple identifier strings are stored to prevent memory leaks.
 */
sealed class TraceEvent {
    abstract val timestampNanos: Long
}

/**
 * Represents an Android frame rendered by the window.
 * Captured via android.view.Window.OnFrameMetricsAvailableListener.
 */
data class FrameEvent(
    override val timestampNanos: Long,
    val frameSequence: Long,
    val totalDurationNanos: Long,
    val inputDurationNanos: Long,
    val animationDurationNanos: Long,
    val layoutMeasureDurationNanos: Long,
    val drawDurationNanos: Long,
    val syncDurationNanos: Long,
    val commandIssueDurationNanos: Long,
    val swapBuffersDurationNanos: Long,
    val isJank: Boolean,
    val deadlineNanos: Long
) : TraceEvent()

/**
 * Represents a discrete editor or engine operation (e.g., syntax highlight, text mutation, scroll layout).
 */
data class EditorOpEvent(
    override val timestampNanos: Long,
    val opName: String,
    val durationNanos: Long,
    val threadName: String,
    val isMainThread: Boolean,
    val metadata: String? = null
) : TraceEvent()

/**
 * Represents a recomposition pass of an instrumented Composable.
 */
data class RecompositionEvent(
    override val timestampNanos: Long,
    val component: String,
    val count: Long,
    val durationNanos: Long
) : TraceEvent()

/**
 * Represents a LazyColumn scroll event or viewport shift.
 */
data class ScrollEvent(
    override val timestampNanos: Long,
    val firstVisibleIndex: Int,
    val visibleItemCount: Int,
    val scrollOffset: Int
) : TraceEvent()

/**
 * Snapshot of JVM and native memory usage.
 */
data class MemorySnapshotEvent(
    override val timestampNanos: Long,
    val usedHeapBytes: Long,
    val totalHeapBytes: Long,
    val maxHeapBytes: Long,
    val nativeAllocatedBytes: Long,
    val gcCount: Long
) : TraceEvent()
