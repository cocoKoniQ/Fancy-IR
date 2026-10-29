package io.github.cocokoniq.fancyir.core.ir.engine

import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.IrPattern
import io.github.cocokoniq.fancyir.core.ir.PatternSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

data class FireRequest(
    /** Stable id of the target device; RC5/RC6 toggle state is kept per target. */
    val targetKey: String,
    /** Human-readable command, for the HUD and logs. */
    val label: String,
    val code: IrCode,
    /** Distinct key presses, e.g. five volume detents. */
    val presses: Int = 1,
    val priority: Priority = Priority.NORMAL,
    val issuedAtMs: Long,
) {
    enum class Priority { URGENT, NORMAL, REPEAT }
}

sealed interface FireOutcome {
    val request: FireRequest

    /** Emitted immediately before the pattern goes to the transmitter: sync beam and haptics to this. */
    data class Started(override val request: FireRequest, val pattern: IrPattern, val atMs: Long) : FireOutcome

    data class Dropped(override val request: FireRequest, val reason: String) : FireOutcome

    data class Failed(override val request: FireRequest, val error: Throwable) : FireOutcome
}

data class IrEngineConfig(
    /** Commands older than this when dequeued are dropped: firing late is worse than not firing. */
    val deadlineMs: Long = 300,
    val maxPatternUs: Int = PatternSanitizer.MAX_TRANSMIT_US,
    val maxCoalescedPresses: Int = 10,
)

/**
 * Single-consumer transmit actor (ARCHITECTURE.md §3.3): coalesces bursts, prioritises,
 * enforces deadlines, keeps toggle state and splits long patterns.
 */
class IrEngine(
    private val transmitter: IrTransmitter,
    scope: CoroutineScope,
    private val config: IrEngineConfig = IrEngineConfig(),
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val requests = Channel<FireRequest>(capacity = 64)
    private val _events = MutableSharedFlow<FireOutcome>(extraBufferCapacity = 64)
    val events: SharedFlow<FireOutcome> = _events.asSharedFlow()

    private val toggles = HashMap<String, Boolean>()

    val capabilities: IrCapabilities get() = transmitter.capabilities

    init {
        scope.launch { run() }
    }

    /** Non-blocking; returns false if the queue is full. */
    fun fire(request: FireRequest): Boolean = requests.trySend(request).isSuccess

    private suspend fun run() {
        for (first in requests) {
            val batch = mutableListOf(first)
            while (true) batch += requests.tryReceive().getOrNull() ?: break
            for (request in coalesce(batch).sortedBy { it.priority.ordinal }) process(request)
        }
    }

    private fun coalesce(batch: List<FireRequest>): List<FireRequest> {
        val merged = ArrayList<FireRequest>(batch.size)
        for (request in batch) {
            val last = merged.lastOrNull()
            if (last != null &&
                last.targetKey == request.targetKey &&
                last.code == request.code &&
                last.priority == request.priority &&
                last.presses + request.presses <= config.maxCoalescedPresses
            ) {
                merged[merged.lastIndex] = last.copy(presses = last.presses + request.presses)
            } else {
                merged += request
            }
        }
        return merged
    }

    private suspend fun process(request: FireRequest) {
        if (nowMs() - request.issuedAtMs > config.deadlineMs) {
            _events.tryEmit(FireOutcome.Dropped(request, "stale"))
            return
        }
        val pattern = try {
            render(request)
        } catch (e: RuntimeException) {
            _events.tryEmit(FireOutcome.Failed(request, e))
            return
        }
        _events.tryEmit(FireOutcome.Started(request, pattern, nowMs()))
        try {
            for (chunk in PatternSanitizer.split(pattern, config.maxPatternUs)) transmitter.transmit(chunk)
        } catch (e: RuntimeException) {
            _events.tryEmit(FireOutcome.Failed(request, e))
        }
    }

    private fun render(request: FireRequest): IrPattern {
        var carrierHz = 0
        val timings = ArrayList<IntArray>(request.presses)
        repeat(request.presses) {
            val toggle = if (request.code.usesToggle) nextToggle(request.targetKey) else false
            val signal = request.code.toSignal(toggle)
            carrierHz = signal.carrierHz
            timings += signal.press()
        }
        val all = IntArray(timings.sumOf { it.size })
        var offset = 0
        for (press in timings) {
            press.copyInto(all, offset)
            offset += press.size
        }
        return IrPattern(transmitter.capabilities.clampCarrier(carrierHz), PatternSanitizer.trimTrailingSpace(all))
    }

    /** RC5/RC6 toggle flips on every new press, never on repeats. */
    private fun nextToggle(targetKey: String): Boolean {
        val next = !(toggles[targetKey] ?: true)
        toggles[targetKey] = next
        return next
    }
}
