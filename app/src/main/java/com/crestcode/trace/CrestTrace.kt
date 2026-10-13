package com.crestcode.trace

import android.app.Activity
import android.os.Debug
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CrestTrace: Deep UI Performance Diagnostics & Post-Mortem Logging System.
 * 
 * Provides bounded, low-overhead in-memory tracing of:
 * - Hardware FrameMetrics and jank status
 * - Jetpack Compose recompositions
 * - Editor pipeline operations (syntax highlight, text layout, scrolling, cursor calculations)
 * - Kotlin-to-Rust JNI call durations
 * - JVM and native memory usage
 */
object CrestTrace {

    @Volatile
    var isRecording: Boolean = false
        private set

    private val _isRecordingFlow = MutableStateFlow(false)
    val isRecordingFlow: StateFlow<Boolean> = _isRecordingFlow.asStateFlow()

    private val _lastReportFlow = MutableStateFlow<String?>(null)
    val lastReportFlow: StateFlow<String?> = _lastReportFlow.asStateFlow()

    private val buffer = BoundedEventBuffer(capacity = 5000)
    val aggregator = TraceMetricsAggregator()
    private var frameMetricsCollector: FrameMetricsCollector? = null

    private var sessionStartTimeNanos: Long = 0L
    private var sessionEndTimeNanos: Long = 0L
    private val isStopping = AtomicBoolean(false)

    /**
     * Begins an active tracing session.
     * @param activity Optional activity to attach hardware FrameMetrics listener.
     */
    @Synchronized
    fun startRecording(activity: Activity? = null) {
        if (isRecording) return

        buffer.clear()
        aggregator.reset()
        sessionStartTimeNanos = System.nanoTime()
        sessionEndTimeNanos = 0L
        isRecording = true
        _isRecordingFlow.value = true

        try {
            captureMemorySnapshot()
            if (activity != null) {
                val collector = FrameMetricsCollector { frameEvent ->
                    if (isRecording) {
                        buffer.push(frameEvent)
                        aggregator.recordFrame(frameEvent)
                    }
                }
                collector.attach(activity.window)
                frameMetricsCollector = collector
            }
        } catch (t: Throwable) {
            // Diagnostics must never crash the host application
        }
    }

    /**
     * Stops the active tracing session and compiles the diagnostic report.
     * @return Generated Markdown post-mortem report.
     */
    @Synchronized
    fun stopRecording(): String {
        if (!isRecording) {
            return _lastReportFlow.value ?: "No trace recording active."
        }

        sessionEndTimeNanos = System.nanoTime()
        isRecording = false
        _isRecordingFlow.value = false

        try {
            frameMetricsCollector?.detach()
            frameMetricsCollector = null
            captureMemorySnapshot()
        } catch (t: Throwable) {
            // Fail-safe cleanup
        }

        val report = CrestTraceReporter.generateReport(
            sessionStartTimeNanos = sessionStartTimeNanos,
            sessionEndTimeNanos = sessionEndTimeNanos,
            buffer = buffer,
            aggregator = aggregator
        )

        _lastReportFlow.value = report
        return report
    }

    /**
     * Clears cached reports and resets internal state.
     */
    @Synchronized
    fun clear() {
        if (isRecording) {
            stopRecording()
        }
        buffer.clear()
        aggregator.reset()
        _lastReportFlow.value = null
    }

    /**
     * Times an editor operation and logs its execution duration.
     * When recording is disabled, executes the block directly with near-zero overhead.
     */
    inline fun <T> traceOp(opName: String, metadata: String? = null, block: () -> T): T {
        if (!isRecording) return block()

        val t0 = System.nanoTime()
        val isMain = (Looper.myLooper() == Looper.getMainLooper())
        return try {
            block()
        } finally {
            val dt = System.nanoTime() - t0
            recordEditorOp(opName, dt, metadata, isMain)
        }
    }

    fun recordEditorOp(opName: String, durationNanos: Long, metadata: String? = null, isMainThread: Boolean = true) {
        if (!isRecording) return
        try {
            val event = EditorOpEvent(
                timestampNanos = System.nanoTime(),
                opName = opName,
                durationNanos = durationNanos,
                threadName = Thread.currentThread().name,
                isMainThread = isMainThread,
                metadata = metadata
            )
            buffer.push(event)
            aggregator.recordOp(opName, durationNanos)
        } catch (t: Throwable) {
            // Ignore
        }
    }

    fun recordRecomposition(component: String, count: Long = 1, durationNanos: Long = 0) {
        if (!isRecording) return
        try {
            val event = RecompositionEvent(
                timestampNanos = System.nanoTime(),
                component = component,
                count = count,
                durationNanos = durationNanos
            )
            buffer.push(event)
            aggregator.recordRecomposition(component, count, durationNanos)
        } catch (t: Throwable) {
            // Ignore
        }
    }

    fun recordHighlightLookup(hit: Boolean, durationNanos: Long) {
        if (!isRecording) return
        try {
            aggregator.recordHighlightLookup(hit, durationNanos)
        } catch (t: Throwable) {
            // Ignore
        }
    }

    fun recordTextLayout(durationNanos: Long) {
        if (!isRecording) return
        try {
            aggregator.recordTextLayout(durationNanos)
        } catch (t: Throwable) {
            // Ignore
        }
    }

    fun recordJni(name: String, durationNanos: Long) {
        if (!isRecording) return
        try {
            aggregator.recordJni(name, durationNanos)
        } catch (t: Throwable) {
            // Ignore
        }
    }

    fun recordScroll(firstVisible: Int, count: Int, offset: Int) {
        if (!isRecording) return
        try {
            val event = ScrollEvent(
                timestampNanos = System.nanoTime(),
                firstVisibleIndex = firstVisible,
                visibleItemCount = count,
                scrollOffset = offset
            )
            buffer.push(event)
        } catch (t: Throwable) {
            // Ignore
        }
    }

    fun captureMemorySnapshot() {
        if (!isRecording) return
        try {
            val runtime = Runtime.getRuntime()
            val total = runtime.totalMemory()
            val free = runtime.freeMemory()
            val used = total - free
            val max = runtime.maxMemory()
            val nativeAlloc = Debug.getNativeHeapAllocatedSize()

            val gcCount = try {
                Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0L
            } catch (t: Throwable) {
                0L
            }

            val event = MemorySnapshotEvent(
                timestampNanos = System.nanoTime(),
                usedHeapBytes = used,
                totalHeapBytes = total,
                maxHeapBytes = max,
                nativeAllocatedBytes = nativeAlloc,
                gcCount = gcCount
            )
            buffer.push(event)
        } catch (t: Throwable) {
            // Ignore
        }
    }
}
