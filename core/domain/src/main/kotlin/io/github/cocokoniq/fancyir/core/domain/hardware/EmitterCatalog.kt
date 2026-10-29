package io.github.cocokoniq.fancyir.core.domain.hardware

import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterOffset.Companion.downRight
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterOffset.Companion.right
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterOffset.Companion.unspecified
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterOffset.Companion.upSlightlyRight

/**
 * Rear-plateau emitter positions, transcribed from docs/hardware/rear-camera-ir-blaster-spec.md.
 * Offsets are the spec's approximate figures; where it gives a range the midpoint is used.
 */
object EmitterCatalog {

    private fun rear(id: String, maker: String, models: List<String>, layout: String, position: String, offset: EmitterOffset, aliases: List<String> = emptyList()) =
        EmitterProfile(id, maker, models, EmitterMount.REAR_PLATEAU, layout, position, offset, aliases)

    val profiles: List<EmitterProfile> = listOf(
        // 1. Xiaomi flagships & T-series
        rear("xiaomi-14", "Xiaomi", listOf("Xiaomi 14"), "2×2 squircle, top-left",
            "Bottom-right ring (with flash and laser AF), upper portion", right(22f),
            aliases = listOf("23127PN0CC", "23127PN0CG", "houji")),
        rear("xiaomi-14-pro", "Xiaomi", listOf("Xiaomi 14 Pro"), "2×2 squircle, top-left",
            "Bottom-right ring, above the dual-LED flash", right(24f)),
        rear("xiaomi-15", "Xiaomi", listOf("Xiaomi 15", "Xiaomi 15 Pro"), "2×2 squircle, flash outside on the right",
            "Bottom-right window with laser AF and flicker sensor", right(22f)),
        rear("xiaomi-ultra", "Xiaomi", listOf("Xiaomi 14 Ultra", "Xiaomi 15 Ultra", "Xiaomi 17 Ultra"), "Large centre circle",
            "Top-centre (12 o'clock) of the circle, below the flash array", upSlightlyRight(26f)),
        rear("xiaomi-15t", "Xiaomi", listOf("Xiaomi 15T", "Xiaomi 15T Pro"), "2×2 squircle, top-left",
            "Bottom-right circle with flicker sensor and laser AF", right(21f)),
        rear("xiaomi-17", "Xiaomi", listOf("Xiaomi 17", "Xiaomi 17 Pro", "Xiaomi 17 Pro Max", "Xiaomi 17T", "Xiaomi 17T Pro"),
            "2×2 squircle / plateau", "Bottom-right sensor window under IR-transparent glass", right(22f)),
        // 2. POCO F-series & Redmi K-series
        rear("poco-f6-pro", "Xiaomi", listOf("POCO F6 Pro", "Redmi K70", "Redmi K70 Pro"), "Full-width glass visor",
            "Bottom-right ring of the left 2×2 grid, with the flash", downRight(20f)),
        rear("poco-f7", "Xiaomi", listOf("POCO F7", "Redmi Turbo 4"), "Vertical pill, top-left",
            "Dark dot right of the pill, aligned with the flash strip", right(16f)),
        rear("poco-f7-pro", "Xiaomi", listOf("POCO F7 Pro", "POCO F7 Ultra", "Redmi K80", "Redmi K80 Pro"), "Circular island, top-left",
            "Outside the ring at about 2 o'clock, by the pill flash", right(28f)),
        rear("poco-f8-ultra", "Xiaomi", listOf("POCO F8 Ultra", "POCO F9 Pro", "POCO F9 Ultra"), "Circular / squircle island, left",
            "Right side of the camera deco, aligned with the flash", right(26.5f)),
        // 3. OnePlus
        rear("oneplus-15", "OnePlus", listOf("OnePlus 15", "OnePlus 15R"), "Rounded squircle, top-left",
            "Dark bridge between the left lens pair and the right column", right(14f)),
        rear("oneplus-13s", "OnePlus", listOf("OnePlus 13s", "OnePlus 13T"), "Compact squircle, top-left",
            "Right column, stacked with the flash", right(18f)),
        rear("oneplus-nord", "OnePlus", listOf("OnePlus Nord 5", "OnePlus Nord 6"), "Vertical pill / squircle, top-left",
            "Bottom-right sensor circle beside the flash", downRight(18f)),
        rear("oneplus-ace-5", "OnePlus", listOf("OnePlus Ace 5", "OnePlus Ace 5 Pro", "OnePlus Ace 5 Ultra"), "Circular island, top-left",
            "Bottom-right (4 o'clock) quadrant", downRight(22f)),
        // 4. vivo, iQOO, Realme, Honor, Tecno/Infinix
        rear("vivo-x200", "vivo", listOf("vivo X200", "vivo X200 Pro", "vivo X200 FE"), "Large centred circle, 2×2",
            "Fourth \"dummy\" lens cutout (top- or bottom-right by model)", unspecified(25f)),
        rear("iqoo-13", "iQOO", listOf("iQOO 13"), "Squircle porthole, top-left",
            "Bottom-right corner inside the halo ring", downRight(20f)),
        rear("realme-gt-7-pro", "realme", listOf("realme GT 7 Pro"), "Rectangular squircle, top-left",
            "Right column, above the dual-LED flash pill", right(19f)),
        rear("honor-magic", "HONOR", listOf("HONOR Magic V5", "HONOR Magic7 Pro", "HONOR Magic 7 Pro", "HONOR Magic8 Pro", "HONOR Magic 8 Pro", "HONOR 600 Pro"),
            "Centred octagon / circle", "Top- or bottom-centre bezel, opposite the flash", unspecified(24f)),
        rear("tecno-infinix", "Tecno / Infinix", listOf("TECNO Spark Go 1", "Infinix GT 30 Pro"), "Top-left squircle",
            "Secondary decorative ring on the right of the island", right(20f)),
    )

    fun byId(id: String): EmitterProfile? = profiles.firstOrNull { it.id == id }
}

/** What Android tells us about the phone; `deviceName` is Settings.Global.DEVICE_NAME, `device` is Build.DEVICE. */
data class DeviceIdentity(val manufacturer: String, val brand: String, val model: String, val deviceName: String?, val device: String = "")

data class EmitterMatch(val profile: EmitterProfile, val exact: Boolean)

/**
 * Finds the phone's emitter profile. `Build.MODEL` is often a code ("23127PN0CG"), so the
 * user-visible device name, which defaults to the marketing name, carries most matches. The
 * longest matching marketing name wins, so "Xiaomi 15 Pro" isn't mistaken for "Xiaomi 15".
 */
object EmitterMatcher {

    fun match(identity: DeviceIdentity, catalog: List<EmitterProfile> = EmitterCatalog.profiles): EmitterMatch? {
        // Model codes and codenames are unambiguous: they win even over a renamed device.
        val codes = setOf(key(identity.model), key(identity.device)) - ""
        catalog.firstOrNull { profile -> profile.aliases.any { key(it) in codes } }?.let { return EmitterMatch(it, exact = true) }

        val candidates = listOfNotNull(identity.deviceName, identity.model, "${identity.brand} ${identity.model}", "${identity.manufacturer} ${identity.model}")
            .map(::key)
            .filter { it.isNotEmpty() }
        val names = catalog.flatMap { profile -> profile.models.map { key(it) to profile } }

        names.firstOrNull { (name, _) -> name in candidates }?.let { return EmitterMatch(it.second, exact = true) }
        return names
            .filter { (name, _) -> candidates.any { candidate -> candidate.contains(name) } }
            .maxByOrNull { it.first.length }
            ?.let { EmitterMatch(it.second, exact = false) }
    }

    private fun key(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }
}
