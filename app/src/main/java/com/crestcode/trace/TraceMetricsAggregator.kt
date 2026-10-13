package com.crestcode.trace

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Real-time aggregator for statistical summaries across frames, operations,
 * recompositions, caches, and text layout metrics.
 */
class TraceMetricsAggregator {

    // --- Frame Statistics ---
    val totalFrames = AtomicLong(0)
    val jankyFrames = AtomicLong(0)
    val severeJankyFrames = AtomicLong(0)
    private val frameDurationsMs = ArrayList<Double>()
    private val slowestFramesList = ArrayList<FrameEvent>()

    // --- Recomposition Statistics ---
    data class RecompositionStat(
        val count: AtomicLong = AtomicLong(0),
        val totalDurationNanos: AtomicLong = AtomicLong(0),
        val maxDurationNanos: AtomicLong = AtomicLong(0)
    )
    val recompositionStats = ConcurrentHashMap<String, RecompositionStat>()

    // --- Editor Operations Statistics ---
    data class OpStat(
        val count: AtomicLong = AtomicLong(0),
        val totalDurationNanos: AtomicLong = AtomicLong(0),
        val maxDurationNanos: AtomicLong = AtomicLong(0),
        val slowCount8ms: AtomicLong = AtomicLong(0),
        val slowCount16ms: AtomicLong = AtomicLong(0)
    )
    val opStats = ConcurrentHashMap<String, OpStat>()

    // --- Highlight Cache Statistics ---
    val cacheHits = AtomicLong(0)
    val cacheMisses = AtomicLong(0)
    val cacheLookupNanos = AtomicLong(0)

    // --- Text Layout Statistics ---
    val textLayoutCount = AtomicLong(0)
    val textLayoutTotalNanos = AtomicLong(0)
    val textLayoutMaxNanos = AtomicLong(0)

    // --- JNI Call Statistics ---
    data class JniStat(
        val count: AtomicLong = AtomicLong(0),
        val totalDurationNanos: AtomicLong = AtomicLong(0),
        val maxDurationNanos: AtomicLong = AtomicLong(0)
    )
    val jniStats = ConcurrentHashMap<String, JniStat>()

    @Synchronized
    fun recordFrame(event: FrameEvent) {
        totalFrames.incrementAndGet()
        val durationMs = event.totalDurationNanos / 1_000_000.0
        frameDurationsMs.add(durationMs)

        if (event.isJank) {
            jankyFrames.incrementAndGet()
        }
        if (event.totalDurationNanos > 33_333_333L) {
            severeJankyFrames.incrementAndGet()
        }

        // Keep top 10 slowest frames
        if (slowestFramesList.size < 10) {
            slowestFramesList.add(event)
            slowestFramesList.sortByDescending { it.totalDurationNanos }
        } else if (event.totalDurationNanos > slowestFramesList.last().totalDurationNanos) {
            slowestFramesList.removeAt(slowestFramesList.size - 1)
            slowestFramesList.add(event)
            slowestFramesList.sortByDescending { it.totalDurationNanos }
        }
    }

    fun recordOp(opName: String, durationNanos: Long) {
        val stat = opStats.computeIfAbsent(opName) { OpStat() }
        stat.count.incrementAndGet()
        stat.totalDurationNanos.addAndGet(durationNanos)
        updateMax(stat.maxDurationNanos, durationNanos)

        if (durationNanos > 8_000_000L) {
            stat.slowCount8ms.incrementAndGet()
        }
        if (durationNanos > 16_666_666L) {
            stat.slowCount16ms.incrementAndGet()
        }
    }

    fun recordRecomposition(component: String, count: Long = 1, durationNanos: Long = 0) {
        val stat = recompositionStats.computeIfAbsent(component) { RecompositionStat() }
        stat.count.addAndGet(count)
        stat.totalDurationNanos.addAndGet(durationNanos)
        if (durationNanos > 0) {
            updateMax(stat.maxDurationNanos, durationNanos)
        }
    }

    fun recordHighlightLookup(hit: Boolean, durationNanos: Long) {
        if (hit) {
            cacheHits.incrementAndGet()
        } else {
            cacheMisses.incrementAndGet()
        }
        cacheLookupNanos.addAndGet(durationNanos)
    }

    fun recordTextLayout(durationNanos: Long) {
        textLayoutCount.incrementAndGet()
        textLayoutTotalNanos.addAndGet(durationNanos)
        updateMax(textLayoutMaxNanos, durationNanos)
    }

    fun recordJni(name: String, durationNanos: Long) {
        val stat = jniStats.computeIfAbsent(name) { JniStat() }
        stat.count.incrementAndGet()
        stat.totalDurationNanos.addAndGet(durationNanos)
        updateMax(stat.maxDurationNanos, durationNanos)
    }

    @Synchronized
    fun getPercentiles(): FramePercentiles {
        if (frameDurationsMs.isEmpty()) {
            return FramePercentiles(0.0, 0.0, 0.0, 0.0, 0.0)
        }
        val sorted = ArrayList(frameDurationsMs)
        sorted.sort()
        val n = sorted.size
        fun p(pct: Double): Double {
            val idx = ((pct / 100.0) * (n - 1)).toInt().coerceIn(0, n - 1)
            return sorted[idx]
        }
        return FramePercentiles(
            p50 = p(50.0),
            p90 = p(90.0),
            p95 = p(95.0),
            p99 = p(99.0),
            max = sorted.last()
        )
    }

    @Synchronized
    fun getSlowestFrames(): List<FrameEvent> {
        return ArrayList(slowestFramesList)
    }

    @Synchronized
    fun reset() {
        totalFrames.set(0)
        jankyFrames.set(0)
        severeJankyFrames.set(0)
        frameDurationsMs.clear()
        slowestFramesList.clear()
        recompositionStats.clear()
        opStats.clear()
        cacheHits.set(0)
        cacheMisses.set(0)
        cacheLookupNanos.set(0)
        textLayoutCount.set(0)
        textLayoutTotalNanos.set(0)
        textLayoutMaxNanos.set(0)
        jniStats.clear()
    }

    private fun updateMax(holder: AtomicLong, newValue: Long) {
        var current = holder.get()
        while (newValue > current) {
            if (holder.compareAndSet(current, newValue)) break
            current = holder.get()
        }
    }
}

data class FramePercentiles(
    val p50: Double,
    val p90: Double,
    val p95: Double,
    val p99: Double,
    val max: Double
)
