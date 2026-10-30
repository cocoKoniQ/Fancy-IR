package io.github.cocokoniq.fancyir.core.domain.hardware

import io.github.cocokoniq.fancyir.core.domain.hardware.IslandShape.CIRCLE_CENTER
import io.github.cocokoniq.fancyir.core.domain.hardware.IslandShape.CIRCLE_TOP_LEFT
import io.github.cocokoniq.fancyir.core.domain.hardware.IslandShape.PILL_TOP_LEFT
import io.github.cocokoniq.fancyir.core.domain.hardware.IslandShape.SQUIRCLE_TOP_LEFT
import io.github.cocokoniq.fancyir.core.domain.hardware.IslandShape.VISOR_TOP

/**
 * Verified emitter positions, transcribed from docs/hardware/rear-camera-ir-blaster-spec.md (v2):
 * §2 lists rear-plateau phones, §3 the look-alikes whose emitter is on the top frame.
 */
object EmitterCatalog {

    private fun rear(
        id: String, maker: String, name: String, models: List<String>, shape: IslandShape,
        lens: Pair<Float, Float>, emitter: Pair<Float, Float>, dxMm: Float, dyMm: Float, description: String,
        aliases: List<String> = emptyList(), prefixes: List<String> = emptyList(), prefixBrands: List<String> = listOf(maker),
    ) = PhoneIrProfile(
        id = id, displayName = name, islandShape = shape,
        mainLensNormX = lens.first, mainLensNormY = lens.second,
        irEmitterNormX = emitter.first, irEmitterNormY = emitter.second,
        offsetDxMm = dxMm, offsetDyMm = dyMm, description = description,
        models = models, manufacturer = maker, aliases = aliases, modelPrefixes = prefixes, prefixBrands = prefixBrands,
    )

    private fun topFrame(id: String, maker: String, name: String, models: List<String>) = PhoneIrProfile(
        id = id, displayName = name, mountType = IrMountType.TOP_FRAME, offsetDxMm = 0f, offsetDyMm = 0f,
        description = "On the top frame rail, 90° to the camera. Lock on with the camera, then tilt the phone flat so the top edge points at the device.",
        models = models, manufacturer = maker,
    )

    val profiles: List<PhoneIrProfile> = listOf(
        // §2.A Xiaomi flagships & T-series
        rear("xiaomi-14", "Xiaomi", "Xiaomi 14 / 14 Pro", listOf("Xiaomi 14", "Xiaomi 14 Pro"), SQUIRCLE_TOP_LEFT,
            lens = 0.27f to 0.27f, emitter = 0.50f to 0.82f, dxMm = 10f, dyMm = 20f,
            description = "Tiny window in the lower gap between the bottom two lenses, below the Leica logo. The top-right ring is flash and laser AF.",
            aliases = listOf("23127PN0CC", "23127PN0CG", "23116PN5BC", "houji", "shennong")),
        rear("xiaomi-15", "Xiaomi", "Xiaomi 15 / 15 Pro", listOf("Xiaomi 15", "Xiaomi 15 Pro"), SQUIRCLE_TOP_LEFT,
            lens = 0.27f to 0.27f, emitter = 0.50f to 0.82f, dxMm = 10f, dyMm = 20f,
            description = "Tiny hole between the bottom two lenses. The fourth ring is laser AF; the flash is outside on the right.",
            aliases = listOf("24129PN74C", "24129PN74G", "2410DPN6CC", "dada", "haotian")),
        rear("xiaomi-15t", "Xiaomi", "Xiaomi 15T / 15T Pro", listOf("Xiaomi 15T", "Xiaomi 15T Pro"), SQUIRCLE_TOP_LEFT,
            lens = 0.28f to 0.28f, emitter = 0.72f to 0.72f, dxMm = 18f, dyMm = 18f,
            description = "Bottom-right ring, with the colour spectrum sensor.",
            aliases = listOf("2506BPN68G", "25069PTEBG")),
        rear("xiaomi-17", "Xiaomi", "Xiaomi 17 / 17 Pro", listOf("Xiaomi 17", "Xiaomi 17 Pro"), SQUIRCLE_TOP_LEFT,
            lens = 0.28f to 0.28f, emitter = 0.72f to 0.72f, dxMm = 18f, dyMm = 18f,
            description = "Bottom-right ring, with the 3D ToF sensor.",
            prefixes = listOf("25119PN")),
        rear("xiaomi-ultra", "Xiaomi", "Xiaomi 14 / 15 / 17 Ultra", listOf("Xiaomi 14 Ultra", "Xiaomi 15 Ultra", "Xiaomi 17 Ultra"), CIRCLE_CENTER,
            lens = 0.26f to 0.60f, emitter = 0.50f to 0.82f, dxMm = 14f, dyMm = 12f,
            description = "Centre-bottom sensor window inside the circle, between the lower lenses below the Leica branding.",
            aliases = listOf("24030PN60G", "25010PN30G", "aurora", "xuanyuan")),
        // §2.B POCO F-series & Redmi K / Turbo
        rear("poco-f7", "Xiaomi", "POCO F7 / Redmi Turbo 4", listOf("POCO F7", "Redmi Turbo 4"), PILL_TOP_LEFT,
            lens = 0.35f to 0.28f, emitter = 0.85f to 0.50f, dxMm = 16f, dyMm = 8f,
            description = "Right of the vertical camera pill on the back glass, level with the dual-LED flash.",
            aliases = listOf("25053PC47G", "24129RT7CC")),
        rear("poco-f7-pro", "Xiaomi", "POCO F7 Pro / F7 Ultra", listOf("POCO F7 Pro", "POCO F7 Ultra", "Redmi K80", "Redmi K80 Pro"), CIRCLE_TOP_LEFT,
            lens = 0.28f to 0.30f, emitter = 0.92f to 0.48f, dxMm = 26f, dyMm = 6f,
            description = "Outside the circular island on the right, next to the pill-shaped flash strip.",
            aliases = listOf("24117RK2CG", "24122RKC7G")),
        rear("poco-f8", "Xiaomi", "POCO F8 Pro / F8 Ultra / F9 Ultra", listOf("POCO F8 Pro", "POCO F8 Ultra", "POCO F9 Ultra"), VISOR_TOP,
            lens = 0.24f to 0.30f, emitter = 0.78f to 0.35f, dxMm = 24f, dyMm = 3f,
            description = "Inside the LED flash ring, right of the camera lenses.",
            // Xiaomi codes start with the certification month, which many phones share: POCO-branded only.
            prefixes = listOf("2510", "2601"), prefixBrands = listOf("POCO")),
        // §2.C OnePlus
        rear("oneplus-15", "OnePlus", "OnePlus 15 / 15R", listOf("OnePlus 15", "OnePlus 15R"), SQUIRCLE_TOP_LEFT,
            lens = 0.30f to 0.26f, emitter = 0.45f to 0.50f, dxMm = 6f, dyMm = 11f,
            description = "Centre bridge between the upper and lower camera rings, inside the island."),
        rear("oneplus-ace-5", "OnePlus", "OnePlus Ace 5 / Ace 5 Pro / 13T / 13s", listOf("OnePlus Ace 5", "OnePlus Ace 5 Pro", "OnePlus 13T", "OnePlus 13s"), SQUIRCLE_TOP_LEFT,
            lens = 0.28f to 0.28f, emitter = 0.55f to 0.50f, dxMm = 10f, dyMm = 10f,
            description = "Between the camera sensors, at the right sensor column inside the island."),
        rear("oneplus-nord-6", "OnePlus", "OnePlus Nord 6", listOf("OnePlus Nord 6"), SQUIRCLE_TOP_LEFT,
            lens = 0.28f to 0.28f, emitter = 0.72f to 0.72f, dxMm = 16f, dyMm = 16f,
            description = "Bottom-right black circle inside the camera bump."),
        rear("oneplus-nord-ce6", "OnePlus", "OnePlus Nord CE6", listOf("OnePlus Nord CE6"), PILL_TOP_LEFT,
            lens = 0.28f to 0.28f, emitter = 0.76f to 0.50f, dxMm = 15f, dyMm = 7f,
            description = "In the small vertical window right of the stacked lenses, alongside the LED flash."),
        // §2.D vivo, iQOO & Realme
        rear("iqoo-13", "iQOO", "iQOO 13", listOf("iQOO 13"), SQUIRCLE_TOP_LEFT,
            lens = 0.30f to 0.28f, emitter = 0.68f to 0.72f, dxMm = 14f, dyMm = 16f,
            description = "Bottom-right area inside the halo ring, below and beside the third lens."),
        rear("vivo-x200", "vivo", "vivo X200 / X200 Pro / X200 Pro mini", listOf("vivo X200", "vivo X200 Pro", "vivo X200 Pro mini"), CIRCLE_CENTER,
            lens = 0.28f to 0.72f, emitter = 0.72f to 0.28f, dxMm = 20f, dyMm = -20f,
            description = "Top-right cutout of the 2×2 grid, disguised as a fourth lens ring."),
        rear("realme-gt-7-pro", "realme", "realme GT 7 Pro", listOf("realme GT 7 Pro"), SQUIRCLE_TOP_LEFT,
            lens = 0.28f to 0.28f, emitter = 0.76f to 0.32f, dxMm = 18f, dyMm = 2f,
            description = "Right column of the island, directly above the vertical dual-LED flash."),
        // §3 Top-frame look-alikes: never classify these as rear plateau.
        topFrame("xiaomi-13", "Xiaomi", "Xiaomi 13 / 13 Pro / 13 Ultra", listOf("Xiaomi 13", "Xiaomi 13 Pro", "Xiaomi 13 Ultra")),
        topFrame("xiaomi-14t", "Xiaomi", "Xiaomi 14T / 14T Pro", listOf("Xiaomi 14T", "Xiaomi 14T Pro")),
        topFrame("poco-f6", "Xiaomi", "POCO F6 / F6 Pro", listOf("POCO F6", "POCO F6 Pro", "Redmi K70", "Redmi K70 Pro")),
        topFrame("poco-x7", "Xiaomi", "POCO X7 / X7 Pro", listOf("POCO X7", "POCO X7 Pro")),
        topFrame("redmi-note", "Xiaomi", "Redmi Note 13 / 14 / 15", listOf("Redmi Note 13", "Redmi Note 14", "Redmi Note 15")),
        topFrame("oneplus-12", "OnePlus", "OnePlus 12 / 12R", listOf("OnePlus 12", "OnePlus 12R")),
        topFrame("oneplus-13", "OnePlus", "OnePlus 13 / 13R", listOf("OnePlus 13", "OnePlus 13R")),
        topFrame("oneplus-nord-4-5", "OnePlus", "OnePlus Nord 4 / Nord 5", listOf("OnePlus Nord 4", "OnePlus Nord 5")),
        topFrame("vivo-x300-fe", "vivo", "vivo X300 FE", listOf("vivo X300 FE")),
        topFrame("oppo-find-x8", "OPPO", "OPPO Find X8 / X8 Pro / X8 Ultra", listOf("OPPO Find X8", "OPPO Find X8 Pro", "OPPO Find X8 Ultra")),
        topFrame("honor-magic", "HONOR", "HONOR Magic6 Pro / Magic7 Pro",
            listOf("HONOR Magic6 Pro", "HONOR Magic 6 Pro", "HONOR Magic7 Pro", "HONOR Magic 7 Pro")),
    )

    fun byId(id: String): PhoneIrProfile? = profiles.firstOrNull { it.id == id }
}

/** What Android tells us about the phone; `deviceName` is Settings.Global.DEVICE_NAME, `device` is Build.DEVICE. */
data class DeviceIdentity(val manufacturer: String, val brand: String, val model: String, val deviceName: String?, val device: String = "")

data class EmitterMatch(val profile: PhoneIrProfile, val exact: Boolean)

/**
 * Finds the phone's emitter profile. `Build.MODEL` is often a code ("23127PN0CG"), so the
 * user-visible device name, which defaults to the marketing name, carries most matches. The
 * longest matching marketing name wins, so "Xiaomi 15 Pro" isn't mistaken for "Xiaomi 15", and
 * a top-frame "Xiaomi 14T" isn't mistaken for a rear-plateau "Xiaomi 14".
 */
object EmitterMatcher {

    fun match(identity: DeviceIdentity, catalog: List<PhoneIrProfile> = EmitterCatalog.profiles): EmitterMatch? {
        // Model codes and codenames are unambiguous: they win even over a renamed device.
        val codes = setOf(key(identity.model), key(identity.device)) - ""
        catalog.firstOrNull { profile -> profile.aliases.any { key(it) in codes } }?.let { return EmitterMatch(it, exact = true) }

        val candidates = listOfNotNull(identity.deviceName, identity.model, "${identity.brand} ${identity.model}", "${identity.manufacturer} ${identity.model}")
            .map(::key)
            .filter { it.isNotEmpty() }
        val names = catalog.flatMap { profile -> profile.models.map { key(it) to profile } }

        names.firstOrNull { (name, _) -> name in candidates }?.let { return EmitterMatch(it.second, exact = true) }
        names
            .filter { (name, _) -> candidates.any { candidate -> candidate.contains(name) } }
            .maxByOrNull { it.first.length }
            ?.let { return EmitterMatch(it.second, exact = false) }
        return matchPrefix(identity, catalog)?.let { EmitterMatch(it, exact = false) }
    }

    /**
     * A model-code prefix is a weak signal, shared by every phone certified the same month. It
     * counts only when the device name can't settle it: a factory name ("POCO X8 Pro") that
     * matched nothing above means the phone isn't in the registry, not that the prefix is right.
     */
    private fun matchPrefix(identity: DeviceIdentity, catalog: List<PhoneIrProfile>): PhoneIrProfile? {
        val brands = setOf(key(identity.brand), key(identity.manufacturer)) - ""
        val name = identity.deviceName?.let(::key).orEmpty()
        if (brands.any { name.startsWith(it) }) return null
        val model = key(identity.model)
        return catalog
            .filter { profile -> profile.prefixBrands.any { key(it) in brands } }
            .flatMap { profile -> profile.modelPrefixes.map { key(it) to profile } }
            .filter { (prefix, _) -> model.startsWith(prefix) }
            .maxByOrNull { it.first.length }
            ?.second
    }

    private fun key(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }
}
