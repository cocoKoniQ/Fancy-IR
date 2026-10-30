package io.github.cocokoniq.fancyir.core.domain

import io.github.cocokoniq.fancyir.core.domain.hardware.DeviceIdentity
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterCatalog
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterMatcher
import io.github.cocokoniq.fancyir.core.domain.hardware.IrMountType
import io.github.cocokoniq.fancyir.core.domain.hardware.IslandShape
import io.github.cocokoniq.fancyir.core.domain.hardware.Parallax
import kotlin.math.abs
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmitterRegistryTest {

    private fun identity(name: String?, model: String = "2400ABCDEG", maker: String = "Xiaomi", brand: String = maker) =
        DeviceIdentity(manufacturer = maker, brand = brand, model = model, deviceName = name)

    private fun matchId(name: String?, model: String = "2400ABCDEG", maker: String = "Xiaomi", brand: String = maker) =
        EmitterMatcher.match(identity(name, model, maker, brand))?.profile?.id

    @Test
    fun `the registry holds the v2 spec's 15 rear-plateau and 11 top-frame families`() {
        val (rear, top) = EmitterCatalog.profiles.partition { it.isRearPlateau }
        assertEquals(15, rear.size)
        assertEquals(11, top.size)
        assertEquals(1, EmitterCatalog.profiles.map { it.id }.groupingBy { it }.eachCount().values.max(), "unique ids")
        val names = EmitterCatalog.profiles.flatMap { it.models }.map { it.lowercase() }
        assertEquals(names.size, names.toSet().size, "a marketing name belongs to one family")
        assertTrue(top.all { it.offsetMm == null })
    }

    @Test
    fun `rear-plateau geometry stays inside the island and agrees with the mm offset`() {
        for (profile in EmitterCatalog.profiles.filter { it.isRearPlateau }) {
            val coords = listOf(profile.mainLensNormX, profile.mainLensNormY, profile.irEmitterNormX, profile.irEmitterNormY)
            assertTrue(coords.all { it in 0f..1f }, "${profile.id}: $coords")
            assertTrue(assertNotNull(profile.offsetMm) in 12f..29f, "${profile.id}: ${profile.offsetMm}")
            // The schematic and the beam marker must point the same way.
            assertEquals(sign(profile.offsetDxMm), sign(profile.irEmitterNormX - profile.mainLensNormX), "${profile.id} dx")
            assertEquals(sign(profile.offsetDyMm), sign(profile.irEmitterNormY - profile.mainLensNormY), "${profile.id} dy")
        }
    }

    @Test
    fun `the 1x lens is top-left on squircle phones (the v1 schematic bug)`() {
        for (profile in EmitterCatalog.profiles.filter { it.isRearPlateau && it.islandShape == IslandShape.SQUIRCLE_TOP_LEFT }) {
            assertTrue(profile.mainLensNormX < 0.35f && profile.mainLensNormY < 0.35f, profile.id)
        }
    }

    @Test
    fun `xiaomi 14 fires from between the bottom two lenses`() {
        val xiaomi14 = assertNotNull(EmitterCatalog.byId("xiaomi-14"))
        assertEquals(0.50f, xiaomi14.irEmitterNormX)
        assertEquals(0.82f, xiaomi14.irEmitterNormY)
        assertEquals(10f, xiaomi14.offsetDxMm)
        assertEquals(20f, xiaomi14.offsetDyMm)
        assertTrue(abs(assertNotNull(xiaomi14.offsetMm) - 22f) < 1f)
    }

    @Test
    fun `device name picks the most specific marketing name`() {
        assertEquals("xiaomi-15", matchId("Xiaomi 15 Pro"))
        assertEquals("xiaomi-15t", matchId("Xiaomi 15T Pro"))
        assertEquals("xiaomi-ultra", matchId("Xiaomi 15 Ultra"))
        assertEquals("poco-f7-pro", matchId("POCO F7 Pro", brand = "POCO"))
        assertEquals("poco-f7", matchId("POCO F7", brand = "POCO"))
        assertEquals("oneplus-ace-5", matchId("OnePlus 13s", maker = "OnePlus"))
        assertEquals("oneplus-nord-ce6", matchId("OnePlus Nord CE6", maker = "OnePlus"))
        assertEquals("oneplus-nord-6", matchId("OnePlus Nord 6", maker = "OnePlus"))
    }

    @Test
    fun `top-frame look-alikes are never classified as rear plateau`() {
        val lookAlikes = listOf(
            "Xiaomi 14T" to "Xiaomi", "Xiaomi 14T Pro" to "Xiaomi", "Xiaomi 13 Ultra" to "Xiaomi", "POCO F6 Pro" to "Xiaomi",
            "Redmi Note 14 Pro+ 5G" to "Xiaomi", "OnePlus 13" to "OnePlus", "OnePlus 13R" to "OnePlus", "OnePlus Nord 5" to "OnePlus",
            "HONOR Magic7 Pro" to "HONOR", "OPPO Find X8 Pro" to "OPPO", "vivo X300 FE" to "vivo",
        )
        for ((name, maker) in lookAlikes) {
            val match = assertNotNull(EmitterMatcher.match(identity(name, maker = maker)), name)
            assertEquals(IrMountType.TOP_FRAME, match.profile.mountType, name)
        }
    }

    @Test
    fun `renamed devices still match by containment, codes alone don't`() {
        val match = assertNotNull(EmitterMatcher.match(identity("Dana's OnePlus 15", model = "CPH2747", maker = "OnePlus")))
        assertEquals("oneplus-15", match.profile.id)
        assertEquals(false, match.exact)
        assertNull(EmitterMatcher.match(identity(null)))
    }

    @Test
    fun `xiaomi 14 matches by model code or codename even when renamed`() {
        assertEquals("xiaomi-14", matchId("Kitchen phone", model = "23127PN0CG"))
        assertEquals("xiaomi-14", matchId("Kitchen phone", model = "23116PN5BC"))
        assertEquals("xiaomi-14", EmitterMatcher.match(DeviceIdentity("Xiaomi", "Xiaomi", "unknown", null, device = "houji"))?.profile?.id)
        assertEquals("xiaomi-14", EmitterMatcher.match(DeviceIdentity("Xiaomi", "Xiaomi", "unknown", null, device = "shennong"))?.profile?.id)
        assertEquals("xiaomi-14", matchId("Xiaomi 14", model = "X"))
    }

    @Test
    fun `model prefixes count only for renamed phones of the right brand`() {
        assertEquals("xiaomi-17", matchId("Living room", model = "25119PN0EC"))
        assertEquals(false, EmitterMatcher.match(identity("Living room", model = "25119PN0EC"))?.exact)
        assertEquals("poco-f8", matchId("Sam's phone", model = "2510DPC44G", brand = "POCO"))
        // A Redmi certified the same month isn't a POCO F8.
        assertNull(matchId("Sam's phone", model = "2510DRK44C", brand = "Redmi"))
        // A factory name the registry doesn't know beats a shared prefix.
        assertNull(matchId("POCO X8 Pro", model = "2601APC8EG", brand = "POCO"))
    }

    @Test
    fun `parallax is under a degree at 2 m and visible point-blank`() {
        assertTrue(Parallax.angularErrorDeg(22f, 2f) < 0.7f)
        assertTrue(Parallax.angularErrorDeg(22f, 0.2f) > 5f)
    }

    @Test
    fun `a down-right emitter appears down and left of the reticle on screen`() {
        val xiaomi14 = assertNotNull(EmitterCatalog.byId("xiaomi-14"))
        val (dx, dy) = assertNotNull(Parallax.screenOffsetPx(xiaomi14.offsetDxMm, xiaomi14.offsetDyMm, distanceM = 0.2f, horizontalFovDeg = 75f, viewWidthPx = 1080f))
        assertTrue(dx < -25f, "left on screen, was $dx")
        assertTrue(dy > 50f, "below the reticle, was $dy")
        val vivo = assertNotNull(EmitterCatalog.byId("vivo-x200"))
        assertTrue(assertNotNull(Parallax.screenOffsetPx(vivo.offsetDxMm, vivo.offsetDyMm, 0.2f, 75f, 1080f)).second < 0f, "vivo's emitter is above its lens")
        assertNull(Parallax.screenOffsetPx(10f, 20f, 0f, 75f, 1080f))
        assertEquals(2f, Parallax.distanceFromDiopters(0.5f))
        assertNull(Parallax.distanceFromDiopters(0f))
    }
}
