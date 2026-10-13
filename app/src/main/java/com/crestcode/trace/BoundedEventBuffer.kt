package com.crestcode.trace

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe bounded circular ring buffer for storing [TraceEvent] records.
 * 
 * Guards against OutOfMemory errors on memory-constrained Android devices by fixing
 * the buffer capacity. If the buffer fills, older events are overwritten and a dropped-event
 * counter is incremented.
 */
class BoundedEventBuffer(val capacity: Int = 5000) {

    private val buffer = arrayOfNulls<TraceEvent>(capacity)
    private var writeHead: Int = 0
    private var totalPushed: Long = 0
    private val _droppedCount = AtomicLong(0)

    val droppedCount: Long
        get() = _droppedCount.get()

    val totalEventsPushed: Long
        get() = synchronized(this) { totalPushed }

    /**
     * Appends an event to the buffer. If the buffer is full, the oldest event is overwritten
     * and [droppedCount] is incremented.
     */
    @Synchronized
    fun push(event: TraceEvent) {
        if (totalPushed >= capacity) {
            _droppedCount.incrementAndGet()
        }
        buffer[writeHead] = event
        writeHead = (writeHead + 1) % capacity
        totalPushed++
    }

    /**
     * Extracts an ordered snapshot list of all currently buffered events (oldest to newest).
     */
    @Synchronized
    fun snapshot(): List<TraceEvent> {
        val count = totalPushed.coerceAtMost(capacity.toLong()).toInt()
        if (count == 0) return emptyList()

        val result = ArrayList<TraceEvent>(count)
        val start = if (totalPushed < capacity) 0 else writeHead
        for (i in 0 until count) {
            val idx = (start + i) % capacity
            val item = buffer[idx]
            if (item != null) {
                result.add(item)
            }
        }
        return result
    }

    /**
     * Clears all buffered events and resets counters.
     */
    @Synchronized
    fun clear() {
        for (i in buffer.indices) {
            buffer[i] = null
        }
        writeHead = 0
        totalPushed = 0
        _droppedCount.set(0)
    }
}
