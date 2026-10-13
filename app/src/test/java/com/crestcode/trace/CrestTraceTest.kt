package com.crestcode.trace

import org.junit.Assert.*
import org.junit.Test

class CrestTraceTest {

    @Test
    fun testBoundedEventBufferEnforcesCapacityAndTracksDrops() {
        val capacity = 50
        val buffer = BoundedEventBuffer(capacity = capacity)

        assertEquals(0, buffer.totalEventsPushed)
        assertEquals(0, buffer.droppedCount)
        assertTrue(buffer.snapshot().isEmpty())

        // Push 120 events (more than double the capacity)
        for (i in 1..120) {
            buffer.push(
                EditorOpEvent(
                    timestampNanos = i * 1_000_000L,
                    opName = "op_$i",
                    durationNanos = i * 10_000L,
                    threadName = "main",
                    isMainThread = true
                )
            )
        }

        assertEquals(120, buffer.totalEventsPushed)
        assertEquals(70, buffer.droppedCount) // 120 - 50 = 70 dropped

        val snapshot = buffer.snapshot()
        assertEquals(50, snapshot.size)

        // Oldest preserved event should be op_71, newest should be op_120
        val firstEvent = snapshot.first() as EditorOpEvent
        val lastEvent = snapshot.last() as EditorOpEvent
        assertEquals("op_71", firstEvent.opName)
        assertEquals("op_120", lastEvent.opName)

        // Clear buffer
        buffer.clear()
        assertEquals(0, buffer.totalEventsPushed)
        assertEquals(0, buffer.droppedCount)
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test
    fun testMetricsAggregatorStatisticalComputations() {
        val aggregator = TraceMetricsAggregator()

        // Record simulated frames: 10ms, 12ms, 16ms, 20ms (jank), 40ms (severe jank)
        val durationsMs = listOf(10L, 12L, 16L, 20L, 40L)
        for ((idx, ms) in durationsMs.withIndex()) {
            aggregator.recordFrame(
                FrameEvent(
                    timestampNanos = idx * 100_000_000L,
                    frameSequence = (idx + 1).toLong(),
                    totalDurationNanos = ms * 1_000_000L,
                    inputDurationNanos = 1_000_000L,
                    animationDurationNanos = 1_000_000L,
                    layoutMeasureDurationNanos = 3_000_000L,
                    drawDurationNanos = 4_000_000L,
                    syncDurationNanos = 1_000_000L,
                    commandIssueDurationNanos = 1_000_000L,
                    swapBuffersDurationNanos = 1_000_000L,
                    isJank = (ms > 16),
                    deadlineNanos = 16_666_666L
                )
            )
        }

        assertEquals(5, aggregator.totalFrames.get())
        assertEquals(2, aggregator.jankyFrames.get()) // 20ms and 40ms
        assertEquals(1, aggregator.severeJankyFrames.get()) // 40ms

        val percentiles = aggregator.getPercentiles()
        assertEquals(16.0, percentiles.p50, 0.01)
        assertEquals(40.0, percentiles.max, 0.01)

        val slowest = aggregator.getSlowestFrames()
        assertEquals(5, slowest.size)
        assertEquals(40_000_000L, slowest.first().totalDurationNanos)

        // Record editor ops
        aggregator.recordOp("applyChange", 5_000_000L)
        aggregator.recordOp("applyChange", 10_000_000L) // > 8ms
        aggregator.recordOp("applyChange", 20_000_000L) // > 16ms

        val opStat = aggregator.opStats["applyChange"]
        assertNotNull(opStat)
        assertEquals(3, opStat!!.count.get())
        assertEquals(35_000_000L, opStat.totalDurationNanos.get())
        assertEquals(20_000_000L, opStat.maxDurationNanos.get())
        assertEquals(2, opStat.slowCount8ms.get())
        assertEquals(1, opStat.slowCount16ms.get())

        // Record cache lookups
        aggregator.recordHighlightLookup(hit = true, durationNanos = 2_000L)
        aggregator.recordHighlightLookup(hit = true, durationNanos = 3_000L)
        aggregator.recordHighlightLookup(hit = false, durationNanos = 50_000L)

        assertEquals(2, aggregator.cacheHits.get())
        assertEquals(1, aggregator.cacheMisses.get())
        assertEquals(55_000L, aggregator.cacheLookupNanos.get())
    }

    @Test
    fun testReportGeneratorProducesCompleteSections() {
        val buffer = BoundedEventBuffer(capacity = 100)
        val aggregator = TraceMetricsAggregator()

        val start = 1_000_000_000L
        val end = 4_000_000_000L // 3 seconds session

        // Push some events
        aggregator.recordFrame(
            FrameEvent(
                timestampNanos = start + 500_000_000L,
                frameSequence = 1,
                totalDurationNanos = 25_000_000L,
                inputDurationNanos = 2_000_000L,
                animationDurationNanos = 1_000_000L,
                layoutMeasureDurationNanos = 12_000_000L,
                drawDurationNanos = 6_000_000L,
                syncDurationNanos = 2_000_000L,
                commandIssueDurationNanos = 1_000_000L,
                swapBuffersDurationNanos = 1_000_000L,
                isJank = true,
                deadlineNanos = 16_666_666L
            )
        )
        aggregator.recordRecomposition("EditorLineRow", count = 42, durationNanos = 15_000_000L)
        aggregator.recordHighlightLookup(hit = true, durationNanos = 5_000L)
        aggregator.recordHighlightLookup(hit = false, durationNanos = 80_000L)
        aggregator.recordTextLayout(durationNanos = 120_000L)
        aggregator.recordJni("nativeGetHighlightSpansJson", durationNanos = 75_000L)

        val report = CrestTraceReporter.generateReport(start, end, buffer, aggregator)

        assertNotNull(report)
        assertTrue("Report must contain Header", report.contains("# CrestTrace Performance Diagnostic Report"))
        assertTrue("Report must contain Section A", report.contains("## A. Session Metadata"))
        assertTrue("Report must contain Section B", report.contains("## B. Frame Timing & Jank Analysis"))
        assertTrue("Report must contain Section C", report.contains("## C. Jetpack Compose Instrumentation"))
        assertTrue("Report must contain Section D", report.contains("## D. Editor Pipeline Analysis"))
        assertTrue("Report must contain Section E", report.contains("## E. Runtime, Memory & GC Evidence"))
        assertTrue("Report must contain Section F", report.contains("## F. Correlated Timeline of Slow Frames"))
        assertTrue("Report must contain Section G", report.contains("## G. Diagnostic Conclusion & Observations"))

        assertTrue("Report must mention EditorLineRow", report.contains("`EditorLineRow`"))
        assertTrue("Report must mention JNI call", report.contains("`nativeGetHighlightSpansJson`"))
        assertTrue("Report must show hit rate", report.contains("50.0%"))
    }

    @Test
    fun testEmptySessionGeneratesSafeReportWithoutExceptions() {
        val buffer = BoundedEventBuffer(capacity = 100)
        val aggregator = TraceMetricsAggregator()

        val report = CrestTraceReporter.generateReport(1_000_000L, 2_000_000L, buffer, aggregator)
        assertNotNull(report)
        assertTrue(report.contains("# CrestTrace Performance Diagnostic Report"))
        assertTrue(report.contains("0 Rendered Frames"))
    }
}
