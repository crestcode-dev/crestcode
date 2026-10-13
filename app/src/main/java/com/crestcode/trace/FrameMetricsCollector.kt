package com.crestcode.trace

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Attaches to an Android [Window] and receives hardware frame metrics via [Window.OnFrameMetricsAvailableListener].
 * 
 * Runs listener callbacks on a dedicated background [HandlerThread] to ensure zero overhead
 * on the application UI thread.
 */
class FrameMetricsCollector(
    private val onFrameRecorded: (FrameEvent) -> Unit
) {

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var attachedWindow: Window? = null
    private var frameSeq: Long = 0
    private val isAttached = AtomicBoolean(false)

    private val frameMetricsListener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, dropCountSinceLastInvocation ->
        try {
            val totalDuration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val inputDuration = frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
            val animationDuration = frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION)
            val layoutMeasureDuration = frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
            val drawDuration = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
            val syncDuration = frameMetrics.getMetric(FrameMetrics.SYNC_DURATION)
            val commandIssueDuration = frameMetrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)
            val swapBuffersDuration = frameMetrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION)

            val deadlineNanos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    frameMetrics.getMetric(FrameMetrics.DEADLINE)
                } catch (t: Throwable) {
                    16_666_666L
                }
            } else {
                16_666_666L // Default ~60fps deadline (16.67ms)
            }

            val isJank = (totalDuration > deadlineNanos)

            val event = FrameEvent(
                timestampNanos = System.nanoTime(),
                frameSequence = ++frameSeq,
                totalDurationNanos = totalDuration,
                inputDurationNanos = inputDuration,
                animationDurationNanos = animationDuration,
                layoutMeasureDurationNanos = layoutMeasureDuration,
                drawDurationNanos = drawDuration,
                syncDurationNanos = syncDuration,
                commandIssueDurationNanos = commandIssueDuration,
                swapBuffersDurationNanos = swapBuffersDuration,
                isJank = isJank,
                deadlineNanos = deadlineNanos
            )

            onFrameRecorded(event)
        } catch (t: Throwable) {
            // Fail-safe: ensure listener never crashes window rendering
        }
    }

    /**
     * Attaches listener to the provided [window].
     */
    fun attach(window: Window) {
        if (isAttached.compareAndSet(false, true)) {
            try {
                attachedWindow = window
                frameSeq = 0
                val thread = HandlerThread("CrestTrace-Frames")
                thread.start()
                handlerThread = thread
                val bgHandler = Handler(thread.looper)
                handler = bgHandler

                window.addOnFrameMetricsAvailableListener(frameMetricsListener, bgHandler)
            } catch (t: Throwable) {
                isAttached.set(false)
            }
        }
    }

    /**
     * Detaches listener from the window and cleans up background thread.
     */
    fun detach() {
        if (isAttached.compareAndSet(true, false)) {
            try {
                attachedWindow?.removeOnFrameMetricsAvailableListener(frameMetricsListener)
            } catch (t: Throwable) {
                // Ignore failure during window detach
            } finally {
                attachedWindow = null
                handlerThread?.quitSafely()
                handlerThread = null
                handler = null
            }
        }
    }
}
