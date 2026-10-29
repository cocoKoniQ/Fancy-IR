package io.github.cocokoniq.fancyir.core.domain

import io.github.cocokoniq.fancyir.core.domain.hardware.DeviceIdentity
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterCatalog
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterMatcher
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterMount
import io.github.cocokoniq.fancyir.core.domain.hardware.Parallax
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmitterRegistryTest {

    private fun identity(name: String?, model: String = "23127PN0CG", maker: String = "Xiaomi") =
        DeviceIdentity(manufacturer = maker, brand = maker, model = model, deviceName = name)

    @Test
    fun `every spec row is a rear-plateau profile with a 14-28 mm offset`() {
        assertEquals(19, EmitterCatalog.profiles.size)
        for (profile in EmitterCatalog.profiles) {
            assertEquals(EmitterMount.REAR_PLATEAU, profile.mount)
            assertTrue(profile.offset.distanceMm in 14f..28f, "${profile.id}: ${profile.offset}")
        }
        assertEquals(1, EmitterCatalog.profiles.map { it.id }.groupingBy { it }.eachCount().values.max(), "unique ids")
    }

    @Test
    fun `device name picks the most specific marketing name`() {
        assertEquals("xiaomi-15", EmitterMatcher.match(identity("Xiaomi 15 Pro"))?.profile?.id)
        assertEquals("xiaomi-15t", EmitterMatcher.match(identity("Xiaomi 15T Pro"))?.profile?.id)
        assertEquals("xiaomi-ultra", EmitterMatcher.match(identity("Xiaomi 15 Ultra"))?.profile?.id)
        assertEquals("poco-f7-pro", EmitterMatcher.match(identity("POCO F7 Pro"))?.profile?.id)
        assertEquals("poco-f7", EmitterMatcher.match(identity("POCO F7"))?.profile?.id)
    }

    @Test
    fun `renamed devices still match by containment, codes alone don't`() {
        val match = assertNotNull(EmitterMatcher.match(identity("Dana's OnePlus 15", model = "CPH2747", maker = "OnePlus")))
        assertEquals("oneplus-15", match.profile.id)
        assertEquals(false, match.exact)
        assertNull(EmitterMatcher.match(identity(null)))
    }

    @Test
    fun `parallax is under a degree at 2 m and visible point-blank`() {
        assertTrue(Parallax.angularErrorDeg(22f, 2f) < 0.7f)
        assertTrue(Parallax.angularErrorDeg(22f, 0.2f) > 5f)
    }

    @Test
    fun `a right-of-lens emitter appears left of the reticle on screen`() {
        val xiaomi15 = EmitterCatalog.byId("xiaomi-15")!!
        val (dx, dy) = assertNotNull(Parallax.screenOffsetPx(xiaomi15.offset, distanceM = 0.2f, horizontalFovDeg = 75f, viewWidthPx = 1080f))
        assertTrue(dx < -50f, "left on screen, was $dx")
        assertTrue(abs(dy) < 1f)
        assertNull(Parallax.screenOffsetPx(EmitterCatalog.byId("vivo-x200")!!.offset, 0.2f, 75f, 1080f), "direction unknown")
        assertEquals(2f, Parallax.distanceFromDiopters(0.5f))
        assertNull(Parallax.distanceFromDiopters(0f))
    }
}
