package io.github.cocokoniq.fancyir.core.ir

/**
 * Pure protocol encoders. Timings follow the IRP definitions (hifi-remote.com notation);
 * address/command semantics follow Flipper Zero.
 */
object ProtocolEncoders {

    // NEC family: {38k,564}<1,-1|1,-3>(16,-8, payload, 1, ^108m) ; repeat (16,-4,1,^108m)
    private const val NEC_UNIT = 564
    private const val NEC_PERIOD_US = 108_000

    // RC5: {36k,msb,889}<1,-1|-1,1>(1,~F:1:6,T:1,D:5,F:6,^114m)
    private const val RC5_HALF_BIT = 889
    private const val RC5_PERIOD_US = 114_000

    // RC6 mode 0: {36k,444,msb}<-1,1|1,-1>(6,-2,1:1,0:3,<-2,2|2,-2>(T:1),D:8,F:8,^107m)
    private const val RC6_UNIT = 444
    private const val RC6_PERIOD_US = 107_000

    // Sony: {40k,600}<1,-1|2,-1>(4,-1,F:7,D:n,^45m), sent at least 3 times
    private const val SIRC_UNIT = 600
    private const val SIRC_PERIOD_US = 45_000
    private const val SIRC_MIN_FRAMES = 3

    private const val MIN_GAP_US = 10_000

    fun encode(protocol: IrProtocol, address: Long, command: Long, toggle: Boolean = false): IrSignal {
        if (!protocol.isEncodable) throw UnsupportedProtocolException(protocol)
        require(address in 0..protocol.maxAddress) {
            "${protocol.flipperName} address 0x${address.toString(16)} exceeds ${protocol.addressBits} bits"
        }
        require(command in 0..protocol.maxCommand) {
            "${protocol.flipperName} command 0x${command.toString(16)} exceeds ${protocol.commandBits} bits"
        }
        return when (protocol) {
            IrProtocol.NEC -> nec(
                payload = address or (address.inv() and 0xFF shl 8) or (command shl 16) or (command.inv() and 0xFF shl 24),
                bitCount = 32,
            )
            IrProtocol.NEC_EXT -> nec(payload = address or (command shl 16), bitCount = 32)
            IrProtocol.NEC42 -> nec(
                payload = address or (address.inv() and 0x1FFF shl 13) or (command shl 26) or (command.inv() and 0xFF shl 34),
                bitCount = 42,
            )
            IrProtocol.NEC42_EXT -> nec(payload = address or (command shl 26), bitCount = 42)
            IrProtocol.SAMSUNG32 -> samsung32(address, command)
            IrProtocol.RC5 -> rc5(address, command and 0x3F, toggle)
            IrProtocol.RC5X -> rc5(address, command or 0x40, toggle)
            IrProtocol.RC6 -> rc6(address, command, toggle)
            IrProtocol.SIRC -> sirc(command, address, addressBits = 5)
            IrProtocol.SIRC15 -> sirc(command, address, addressBits = 8)
            IrProtocol.SIRC20 -> sirc(command, address, addressBits = 13)
            IrProtocol.KASEIKYO, IrProtocol.RCA, IrProtocol.PIONEER -> throw UnsupportedProtocolException(protocol)
        }
    }

    private fun nec(payload: Long, bitCount: Int): IrSignal {
        val frame = TimingBuilder().apply {
            mark(16 * NEC_UNIT)
            space(8 * NEC_UNIT)
            pulseDistanceLsbFirst(payload, bitCount, NEC_UNIT)
            mark(NEC_UNIT)
            padToPeriod(NEC_PERIOD_US, MIN_GAP_US)
        }.build()
        val repeat = TimingBuilder().apply {
            mark(16 * NEC_UNIT)
            space(4 * NEC_UNIT)
            mark(NEC_UNIT)
            padToPeriod(NEC_PERIOD_US, MIN_GAP_US)
        }.build()
        return IrSignal(carrierHz = 38_000, frame = frame, repeatFrame = repeat)
    }

    /** Samsung32 (NECx2): 4.5/4.5 ms leader, address sent twice, full-frame repeats. */
    private fun samsung32(address: Long, command: Long): IrSignal {
        val payload = address or (address shl 8) or (command shl 16) or (command.inv() and 0xFF shl 24)
        val frame = TimingBuilder().apply {
            mark(8 * NEC_UNIT)
            space(8 * NEC_UNIT)
            pulseDistanceLsbFirst(payload, 32, NEC_UNIT)
            mark(NEC_UNIT)
            padToPeriod(NEC_PERIOD_US, MIN_GAP_US)
        }.build()
        return IrSignal(carrierHz = 38_000, frame = frame)
    }

    private fun TimingBuilder.pulseDistanceLsbFirst(payload: Long, bitCount: Int, unit: Int) {
        for (bit in 0 until bitCount) {
            mark(unit)
            space(if ((payload shr bit) and 1L == 1L) 3 * unit else unit)
        }
    }

    /** RC5/RC5X: Manchester, '1' = space→mark. The second start bit is the inverted command bit 6. */
    private fun rc5(address: Long, command: Long, toggle: Boolean): IrSignal {
        val bits = ArrayList<Boolean>(14).apply {
            add(true)
            add(command and 0x40 == 0L)
            add(toggle)
            addMsbFirst(address, 5)
            addMsbFirst(command, 6)
        }
        val frame = TimingBuilder().apply {
            for (bit in bits) {
                level(isMark = !bit, RC5_HALF_BIT)
                level(isMark = bit, RC5_HALF_BIT)
            }
            padToPeriod(RC5_PERIOD_US, MIN_GAP_US)
        }.build()
        return IrSignal(carrierHz = 36_000, frame = frame)
    }

    /** RC6 mode 0: Manchester, '1' = mark→space, with a double-width toggle bit. */
    private fun rc6(address: Long, command: Long, toggle: Boolean): IrSignal {
        val frame = TimingBuilder().apply {
            mark(6 * RC6_UNIT)
            space(2 * RC6_UNIT)
            rc6Bit(true, RC6_UNIT)
            repeat(3) { rc6Bit(false, RC6_UNIT) }
            rc6Bit(toggle, 2 * RC6_UNIT)
            for (i in 7 downTo 0) rc6Bit((address shr i) and 1L == 1L, RC6_UNIT)
            for (i in 7 downTo 0) rc6Bit((command shr i) and 1L == 1L, RC6_UNIT)
            padToPeriod(RC6_PERIOD_US, MIN_GAP_US)
        }.build()
        return IrSignal(carrierHz = 36_000, frame = frame)
    }

    private fun TimingBuilder.rc6Bit(one: Boolean, halfUs: Int) {
        level(isMark = one, halfUs)
        level(isMark = !one, halfUs)
    }

    /** Sony SIRC: pulse-width, LSB first, command then address. */
    private fun sirc(command: Long, address: Long, addressBits: Int): IrSignal {
        val frame = TimingBuilder().apply {
            mark(4 * SIRC_UNIT)
            space(SIRC_UNIT)
            for (bit in 0 until 7) sircBit((command shr bit) and 1L == 1L)
            for (bit in 0 until addressBits) sircBit((address shr bit) and 1L == 1L)
            padToPeriod(SIRC_PERIOD_US, MIN_GAP_US)
        }.build()
        return IrSignal(carrierHz = 40_000, frame = frame, minFrames = SIRC_MIN_FRAMES)
    }

    private fun TimingBuilder.sircBit(one: Boolean) {
        mark(if (one) 2 * SIRC_UNIT else SIRC_UNIT)
        space(SIRC_UNIT)
    }

    private fun MutableList<Boolean>.addMsbFirst(value: Long, bits: Int) {
        for (i in bits - 1 downTo 0) add((value shr i) and 1L == 1L)
    }
}

class UnsupportedProtocolException(val protocol: IrProtocol) :
    UnsupportedOperationException("No verified encoder for ${protocol.flipperName} yet; use a raw capture")
