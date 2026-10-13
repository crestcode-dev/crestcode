package com.crestcode.trace

import android.os.Build
import android.os.Debug
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the comprehensive post-mortem performance diagnostics report in Markdown format.
 */
object CrestTraceReporter {

    fun generateReport(
        sessionStartTimeNanos: Long,
        sessionEndTimeNanos: Long,
        buffer: BoundedEventBuffer,
        aggregator: TraceMetricsAggregator
    ): String {
        val durationNanos = (sessionEndTimeNanos - sessionStartTimeNanos).coerceAtLeast(1L)
        val durationSec = durationNanos / 1_000_000_000.0
        val durationMs = durationNanos / 1_000_000.0

        val events = buffer.snapshot()
        val percentiles = aggregator.getPercentiles()
        val slowestFrames = aggregator.getSlowestFrames()

        val sb = StringBuilder()

        // Header
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        sb.append("# CrestTrace Performance Diagnostic Report\n\n")
        sb.append("**Generated At:** ${dateFormat.format(Date())}\n\n")

        // ==========================================
        // SECTION A: Session Metadata
        // ==========================================
        sb.append("## A. Session Metadata\n\n")
        sb.append("| Metric | Value |\n")
        sb.append("| :--- | :--- |\n")
        sb.append("| **Application Version** | CrestCode v0.2.0 (Build 2) |\n")
        sb.append("| **Device Hardware** | ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) |\n")
        sb.append("| **Android OS Version** | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) |\n")
        sb.append("| **Supported ABIs** | ${Build.SUPPORTED_ABIS.joinToString(", ")} |\n")
        sb.append("| **Recording Duration** | ${String.format(Locale.US, "%.2f s (%.1f ms)", durationSec, durationMs)} |\n")
        sb.append("| **Total Events Buffered** | ${events.size} |\n")
        sb.append("| **Events Dropped (Buffer Overflow)** | ${buffer.droppedCount} |\n")
        sb.append("| **Instrumentation Mode** | Hardware FrameMetrics (API ${Build.VERSION.SDK_INT}) + Targeted Jetpack Compose Probes + JNI Telemetry |\n\n")

        // ==========================================
        // SECTION B: Frame Analysis
        // ==========================================
        val totalFrames = aggregator.totalFrames.get()
        val jankFrames = aggregator.jankyFrames.get()
        val severeJankFrames = aggregator.severeJankyFrames.get()
        val jankPercent = if (totalFrames > 0) (jankFrames * 100.0 / totalFrames) else 0.0

        sb.append("## B. Frame Timing & Jank Analysis\n\n")
        sb.append("| Metric | Value |\n")
        sb.append("| :--- | :--- |\n")
        sb.append("| **Total Rendered Frames** | $totalFrames |\n")
        sb.append("| **Janky Frames (>16.6ms)** | $jankFrames (${String.format(Locale.US, "%.1f%%", jankPercent)}) |\n")
        sb.append("| **Severe Jank (>33.3ms / <30fps)** | $severeJankFrames |\n")
        sb.append("| **p50 (Median) Frame Duration** | ${String.format(Locale.US, "%.2f ms", percentiles.p50)} |\n")
        sb.append("| **p90 Frame Duration** | ${String.format(Locale.US, "%.2f ms", percentiles.p90)} |\n")
        sb.append("| **p95 Frame Duration** | ${String.format(Locale.US, "%.2f ms", percentiles.p95)} |\n")
        sb.append("| **p99 Frame Duration** | ${String.format(Locale.US, "%.2f ms", percentiles.p99)} |\n")
        sb.append("| **Max Frame Duration** | ${String.format(Locale.US, "%.2f ms", percentiles.max)} |\n\n")

        if (slowestFrames.isNotEmpty()) {
            sb.append("### Slowest Frames Breakdown\n\n")
            sb.append("| Frame # | Total (ms) | Input (ms) | Anim (ms) | Layout/Measure (ms) | Draw (ms) | Sync (ms) | Cmd/Swap (ms) |\n")
            sb.append("| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |\n")
            for (f in slowestFrames.take(5)) {
                val tot = f.totalDurationNanos / 1_000_000.0
                val inp = f.inputDurationNanos / 1_000_000.0
                val anim = f.animationDurationNanos / 1_000_000.0
                val lay = f.layoutMeasureDurationNanos / 1_000_000.0
                val drw = f.drawDurationNanos / 1_000_000.0
                val sync = f.syncDurationNanos / 1_000_000.0
                val cmd = (f.commandIssueDurationNanos + f.swapBuffersDurationNanos) / 1_000_000.0
                sb.append("| #${f.frameSequence} | ${String.format(Locale.US, "%.2f", tot)} | ${String.format(Locale.US, "%.2f", inp)} | ${String.format(Locale.US, "%.2f", anim)} | ${String.format(Locale.US, "%.2f", lay)} | ${String.format(Locale.US, "%.2f", drw)} | ${String.format(Locale.US, "%.2f", sync)} | ${String.format(Locale.US, "%.2f", cmd)} |\n")
            }
            sb.append("\n")
        }

        sb.append("> **Platform Metric Availability:** GPU core frequency and proprietary GPU driver pipeline stages are not exposed by Android public APIs and are marked **Unavailable**. Frame durations represent window lifecycle intervals measured via HWUI.\n\n")

        // ==========================================
        // SECTION C: Compose Analysis
        // ==========================================
        sb.append("## C. Jetpack Compose Instrumentation\n\n")
        if (aggregator.recompositionStats.isNotEmpty()) {
            sb.append("| Component | Recompositions | Total Duration (ms) | Max Duration (ms) | Recompositions / sec |\n")
            sb.append("| :--- | :---: | :---: | :---: | :---: |\n")
            for ((comp, stat) in aggregator.recompositionStats.entries.sortedByDescending { it.value.count.get() }) {
                val count = stat.count.get()
                val totalMs = stat.totalDurationNanos.get() / 1_000_000.0
                val maxMs = stat.maxDurationNanos.get() / 1_000_000.0
                val perSec = if (durationSec > 0) count / durationSec else 0.0
                sb.append("| `$comp` | $count | ${String.format(Locale.US, "%.2f", totalMs)} | ${String.format(Locale.US, "%.2f", maxMs)} | ${String.format(Locale.US, "%.1f", perSec)} |\n")
            }
            sb.append("\n")
        } else {
            sb.append("No explicit recomposition events recorded during this session.\n\n")
        }
        sb.append("> **Measurement Limitation:** Jetpack Compose compiler in production does not emit per-composable bytecode tracing hooks without the tracing runtime library. Measurements reflect explicit targeted telemetry hooks placed on primary editor composables.\n\n")

        // ==========================================
        // SECTION D: Editor Pipeline Analysis
        // ==========================================
        sb.append("## D. Editor Pipeline Analysis\n\n")

        // Syntax Highlighting Cache
        val hits = aggregator.cacheHits.get()
        val misses = aggregator.cacheMisses.get()
        val totalLookups = hits + misses
        val hitRate = if (totalLookups > 0) (hits * 100.0 / totalLookups) else 0.0
        val avgLookupUs = if (totalLookups > 0) (aggregator.cacheLookupNanos.get() / 1000.0 / totalLookups) else 0.0

        sb.append("### 1. Syntax Highlighting Cache\n\n")
        sb.append("| Metric | Value |\n")
        sb.append("| :--- | :--- |\n")
        sb.append("| **Cache Lookups** | $totalLookups |\n")
        sb.append("| **Cache Hits** | $hits (${String.format(Locale.US, "%.1f%%", hitRate)}) |\n")
        sb.append("| **Cache Misses (Parser Invocations)** | $misses |\n")
        sb.append("| **Avg Lookup / Retrieval Latency** | ${String.format(Locale.US, "%.2f µs", avgLookupUs)} |\n\n")

        // Text Layout Callbacks
        val tlCount = aggregator.textLayoutCount.get()
        val tlTotalMs = aggregator.textLayoutTotalNanos.get() / 1_000_000.0
        val tlMaxMs = aggregator.textLayoutMaxNanos.get() / 1_000_000.0
        val tlAvgUs = if (tlCount > 0) (aggregator.textLayoutTotalNanos.get() / 1000.0 / tlCount) else 0.0

        sb.append("### 2. Text Layout (`onTextLayout`)\n\n")
        sb.append("| Metric | Value |\n")
        sb.append("| :--- | :--- |\n")
        sb.append("| **Layout Callback Invocations** | $tlCount |\n")
        sb.append("| **Total Layout Duration** | ${String.format(Locale.US, "%.2f ms", tlTotalMs)} |\n")
        sb.append("| **Avg Callback Duration** | ${String.format(Locale.US, "%.2f µs", tlAvgUs)} |\n")
        sb.append("| **Max Callback Duration** | ${String.format(Locale.US, "%.2f ms", tlMaxMs)} |\n\n")

        // Editor Operations
        if (aggregator.opStats.isNotEmpty()) {
            sb.append("### 3. Editor Operations\n\n")
            sb.append("| Operation | Count | Total (ms) | Max (ms) | Avg (µs) | Slow (>8ms) | Severe (>16ms) |\n")
            sb.append("| :--- | :---: | :---: | :---: | :---: | :---: | :---: |\n")
            for ((name, stat) in aggregator.opStats.entries.sortedByDescending { it.value.totalDurationNanos.get() }) {
                val count = stat.count.get()
                val totalMs = stat.totalDurationNanos.get() / 1_000_000.0
                val maxMs = stat.maxDurationNanos.get() / 1_000_000.0
                val avgUs = if (count > 0) (stat.totalDurationNanos.get() / 1000.0 / count) else 0.0
                sb.append("| `$name` | $count | ${String.format(Locale.US, "%.2f", totalMs)} | ${String.format(Locale.US, "%.2f", maxMs)} | ${String.format(Locale.US, "%.1f", avgUs)} | ${stat.slowCount8ms.get()} | ${stat.slowCount16ms.get()} |\n")
            }
            sb.append("\n")
        }

        // JNI Calls
        if (aggregator.jniStats.isNotEmpty()) {
            sb.append("### 4. Kotlin-to-Rust JNI Calls\n\n")
            sb.append("| Native Call | Invocations | Total (ms) | Max (ms) | Avg (µs) |\n")
            sb.append("| :--- | :---: | :---: | :---: | :---: |\n")
            for ((name, stat) in aggregator.jniStats.entries.sortedByDescending { it.value.totalDurationNanos.get() }) {
                val count = stat.count.get()
                val totalMs = stat.totalDurationNanos.get() / 1_000_000.0
                val maxMs = stat.maxDurationNanos.get() / 1_000_000.0
                val avgUs = if (count > 0) (stat.totalDurationNanos.get() / 1000.0 / count) else 0.0
                sb.append("| `$name` | $count | ${String.format(Locale.US, "%.2f", totalMs)} | ${String.format(Locale.US, "%.2f", maxMs)} | ${String.format(Locale.US, "%.1f", avgUs)} |\n")
            }
            sb.append("\n")
        }

        // ==========================================
        // SECTION E: Runtime Analysis
        // ==========================================
        val runtime = Runtime.getRuntime()
        val totalHeapMb = runtime.totalMemory() / (1024.0 * 1024.0)
        val freeHeapMb = runtime.freeMemory() / (1024.0 * 1024.0)
        val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
        val maxHeapMb = runtime.maxMemory() / (1024.0 * 1024.0)
        val nativeAllocMb = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)

        val gcCountStr = try {
            Debug.getRuntimeStat("art.gc.gc-count") ?: "Unavailable"
        } catch (t: Throwable) {
            "Unavailable"
        }

        sb.append("## E. Runtime, Memory & GC Evidence\n\n")
        sb.append("| Metric | Value |\n")
        sb.append("| :--- | :--- |\n")
        sb.append("| **JVM Used Heap** | ${String.format(Locale.US, "%.2f MB", usedHeapMb)} |\n")
        sb.append("| **JVM Total Heap** | ${String.format(Locale.US, "%.2f MB", totalHeapMb)} |\n")
        sb.append("| **JVM Max Available Heap** | ${String.format(Locale.US, "%.2f MB", maxHeapMb)} |\n")
        sb.append("| **Native Heap Allocated** | ${String.format(Locale.US, "%.2f MB", nativeAllocMb)} |\n")
        sb.append("| **ART Garbage Collections** | $gcCountStr |\n\n")

        // ==========================================
        // SECTION F: Correlated Timeline
        // ==========================================
        sb.append("## F. Correlated Timeline of Slow Frames\n\n")
        if (slowestFrames.isNotEmpty()) {
            sb.append("Correlating operations that executed within ±20 ms of the slowest frames:\n\n")
            for (f in slowestFrames.take(5)) {
                val relMs = (f.timestampNanos - sessionStartTimeNanos) / 1_000_000.0
                val totMs = f.totalDurationNanos / 1_000_000.0
                sb.append("- **Frame #${f.frameSequence}** at `+${String.format(Locale.US, "%.2f", relMs)} ms`: Duration = **${String.format(Locale.US, "%.2f ms", totMs)}** (Layout/Measure: ${String.format(Locale.US, "%.2f", f.layoutMeasureDurationNanos / 1_000_000.0)} ms, Draw: ${String.format(Locale.US, "%.2f", f.drawDurationNanos / 1_000_000.0)} ms)\n")

                // Find nearby events
                val nearby = events.filter { ev ->
                    ev !is FrameEvent && kotlin.math.abs(ev.timestampNanos - f.timestampNanos) < 20_000_000L
                }
                if (nearby.isNotEmpty()) {
                    for (ne in nearby.take(4)) {
                        when (ne) {
                            is EditorOpEvent -> {
                                val dur = ne.durationNanos / 1_000_000.0
                                sb.append("  - ↳ Op: `${ne.opName}` (${String.format(Locale.US, "%.2f ms", dur)}) [Thread: ${ne.threadName}]\n")
                            }
                            is RecompositionEvent -> {
                                val dur = ne.durationNanos / 1_000_000.0
                                sb.append("  - ↳ Recompose: `${ne.component}` (${String.format(Locale.US, "%.2f ms", dur)})\n")
                            }
                            is ScrollEvent -> {
                                sb.append("  - ↳ Scroll: First visible line = ${ne.firstVisibleIndex}, offset = ${ne.scrollOffset} px\n")
                            }
                            else -> {}
                        }
                    }
                } else {
                    sb.append("  - ↳ *No recorded editor operations in close proximity; latency likely in HWUI RenderThread or Android window sync.*\n")
                }
            }
            sb.append("\n")
        } else {
            sb.append("No slow frames recorded to correlate.\n\n")
        }

        // ==========================================
        // SECTION G: Diagnostic Conclusion
        // ==========================================
        sb.append("## G. Diagnostic Conclusion & Observations\n\n")
        sb.append("### Observed Facts:\n")
        if (jankPercent > 20.0) {
            sb.append("- **High Jank Rate:** ${String.format(Locale.US, "%.1f%%", jankPercent)} of frames exceeded the rendering deadline.\n")
        } else {
            sb.append("- **Controlled Jank Rate:** ${String.format(Locale.US, "%.1f%%", jankPercent)} of frames exceeded the rendering deadline.\n")
        }

        val dominantStage = if (slowestFrames.isNotEmpty()) {
            val f = slowestFrames.first()
            val stages = listOf(
                "Layout/Measure" to f.layoutMeasureDurationNanos,
                "Draw" to f.drawDurationNanos,
                "Sync" to f.syncDurationNanos,
                "Command/Swap" to (f.commandIssueDurationNanos + f.swapBuffersDurationNanos),
                "Input" to f.inputDurationNanos,
                "Animation" to f.animationDurationNanos
            )
            stages.maxByOrNull { it.second }?.first ?: "Unknown"
        } else "None"

        sb.append("- **Dominant Frame Phase in Slowest Frames:** `$dominantStage`.\n")
        if (hits + misses > 0) {
            sb.append("- **Highlight Cache Hit Rate:** ${String.format(Locale.US, "%.1f%%", hitRate)} (Total Lookups: $totalLookups, Misses: $misses).\n")
        }
        sb.append("- **Text Layout Callbacks:** $tlCount invocations, peak duration ${String.format(Locale.US, "%.2f ms", tlMaxMs)}.\n\n")

        sb.append("### Potential Suspects:\n")
        if (dominantStage == "Layout/Measure") {
            sb.append("1. **LazyColumn Text Measurement During Rapid Scrolling:** When newly visible lines enter the viewport, `Text(onTextLayout = ...)` computes line geometry synchronously during the layout pass.\n")
        } else if (dominantStage == "Draw" || dominantStage == "Sync") {
            sb.append("1. **Hardware Canvas Render Pass:** Heavy rendering pass or multi-layer Canvas draws in HWUI.\n")
        }
        if (misses > 20 && hitRate < 70.0) {
            sb.append("2. **Syntax Highlight Cache Churn:** High miss rate is forcing on-demand JNI token span extraction during scroll.\n")
        }
        sb.append("3. **Recomposition Density:** Frequent state emissions causing layout passes on visible line rows.\n\n")

        sb.append("### Suggested Controlled Experiments:\n")
        sb.append("1. **Viewport Pre-warming:** Test caching text layout results or pre-rendering off-screen lines to measure if layout duration drops.\n")
        sb.append("2. **Perfetto System Trace:** For deeper CPU core scheduling and kernel thread migration analysis, capture a system trace using `adb shell record_android_trace`.\n\n")

        return sb.toString()
    }
}
