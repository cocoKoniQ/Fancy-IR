package io.github.cocokoniq.fancyir.core.ir.engine

import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.IrProtocol
import io.github.cocokoniq.fancyir.core.ir.ProtocolEncoders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IrEngineTest {

    private val volumeUp = IrCode.Parsed(IrProtocol.NEC, 0x04, 0x02)
    private val power = IrCode.Parsed(IrProtocol.NEC, 0x04, 0x08)
    private var now = 1_000L

    private fun request(code: IrCode, priority: FireRequest.Priority = FireRequest.Priority.NORMAL, issuedAt: Long = now) =
        FireRequest(targetKey = "tv", label = code.toString(), code = code, priority = priority, issuedAtMs = issuedAt)

    private fun TestScope.engine(transmitter: IrTransmitter) = IrEngine(transmitter, backgroundScope, nowMs = { now })

    @Test
    fun `a burst of identical presses becomes one transmit with all presses`() = runTest {
        val tx = RecordingIrTransmitter()
        val engine = engine(tx)

        repeat(3) { engine.fire(request(volumeUp)) }
        runCurrent()

        assertEquals(1, tx.sent.size)
        val onePress = ProtocolEncoders.encode(IrProtocol.NEC, 0x04, 0x02).press()
        assertEquals(3 * onePress.sum().toLong() - onePress.last(), tx.sent.single().durationUs)
    }

    @Test
    fun `urgent requests jump the queue`() = runTest {
        val tx = RecordingIrTransmitter()
        val engine = engine(tx)

        engine.fire(request(volumeUp))
        engine.fire(request(power, FireRequest.Priority.URGENT))
        runCurrent()

        val powerPattern = ProtocolEncoders.encode(IrProtocol.NEC, 0x04, 0x08).press().dropLast(1)
        assertEquals(powerPattern, tx.sent.first().timingsUs.toList())
    }

    @Test
    fun `stale requests are dropped, not fired late`() = runTest {
        val tx = RecordingIrTransmitter()
        val engine = engine(tx)
        val outcomes = mutableListOf<FireOutcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.events.toList(outcomes) }

        engine.fire(request(volumeUp, issuedAt = now - 1_000))
        runCurrent()

        assertTrue(tx.sent.isEmpty())
        assertIs<FireOutcome.Dropped>(outcomes.single())
    }

    @Test
    fun `RC5 toggle flips between presses`() = runTest {
        val tx = RecordingIrTransmitter()
        val engine = engine(tx)
        val rc5 = IrCode.Parsed(IrProtocol.RC5, 0x00, 0x10)

        engine.fire(request(rc5))
        runCurrent()
        engine.fire(request(rc5))
        runCurrent()

        assertEquals(2, tx.sent.size)
        assertNotEquals(tx.sent[0], tx.sent[1])
    }

    @Test
    fun `carrier is clamped into the emitter's supported range`() = runTest {
        val tx = RecordingIrTransmitter(IrCapabilities(hasEmitter = true, carrierRangesHz = listOf(30_000..37_000)))
        val engine = engine(tx)

        engine.fire(request(volumeUp))
        runCurrent()

        assertEquals(37_000, tx.sent.single().carrierHz)
    }

    @Test
    fun `unsupported protocols fail without blocking the queue`() = runTest {
        val tx = RecordingIrTransmitter()
        val engine = engine(tx)
        val outcomes = mutableListOf<FireOutcome>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { engine.events.toList(outcomes) }

        engine.fire(request(IrCode.Parsed(IrProtocol.KASEIKYO, 0x2002, 0x3D)))
        engine.fire(request(power))
        runCurrent()

        assertIs<FireOutcome.Failed>(outcomes.first())
        assertEquals(1, tx.sent.size)
    }
}
