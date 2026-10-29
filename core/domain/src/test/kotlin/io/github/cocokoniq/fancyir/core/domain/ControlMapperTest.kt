package io.github.cocokoniq.fancyir.core.domain

import io.github.cocokoniq.fancyir.core.domain.control.ControlIntent
import io.github.cocokoniq.fancyir.core.domain.control.ControlMapper
import io.github.cocokoniq.fancyir.core.domain.control.ControlProfiles
import io.github.cocokoniq.fancyir.core.domain.gesture.Direction
import io.github.cocokoniq.fancyir.core.domain.gesture.GestureEvent
import io.github.cocokoniq.fancyir.core.irdb.Codeset
import io.github.cocokoniq.fancyir.core.irdb.Command
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.StarterLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ControlMapperTest {

    private val tv = ControlProfiles.forCategory(DeviceCategory.TV)

    @Test
    fun `tv verbs map to mute, volume, channel and power`() {
        assertEquals(listOf(Command.MUTE), (ControlMapper.map(GestureEvent.Tap, tv) as ControlIntent.Send).chain)
        val dial = ControlMapper.map(GestureEvent.Dial(-3), tv) as ControlIntent.Send
        assertEquals(listOf(Command.VOLUME_DOWN) to 3, dial.chain to dial.presses)
        assertEquals(listOf(Command.CHANNEL_UP), (ControlMapper.map(GestureEvent.Step(1), tv) as ControlIntent.Send).chain)
        val power = ControlMapper.map(GestureEvent.PowerReleased, tv) as ControlIntent.Send
        assertEquals(true, power.urgent)
    }

    @Test
    fun `radial slots open layers and the long tail`() {
        assertEquals(ControlIntent.EnterNav, ControlMapper.map(GestureEvent.RadialSelected(Direction.RIGHT), tv))
        assertEquals(ControlIntent.OpenAllCommands, ControlMapper.map(GestureEvent.RadialSelected(Direction.DOWN), tv))
        assertNull(ControlMapper.map(GestureEvent.Charged, tv))
    }

    @Test
    fun `nav layer flicks are d-pad presses`() {
        val intent = ControlMapper.map(GestureEvent.Flick(Direction.LEFT), ControlProfiles.nav) as ControlIntent.Send
        assertEquals(listOf(Command.LEFT), intent.chain)
    }

    @Test
    fun `every starter tv codeset resolves every tv verb`() {
        for (entry in StarterLibrary.entries) {
            val codeset = Codeset.from(StarterLibrary.load(entry))
            for (chain in listOf(tv.tap, tv.dialUp, tv.dialDown, tv.stepNext, tv.stepPrev, tv.power)) {
                checkNotNull(codeset.firstAvailable(chain)) { "${entry.id} cannot resolve $chain" }
            }
        }
    }
}
