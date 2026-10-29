package io.github.cocokoniq.fancyir.core.irdb

/**
 * Hand-written code sets bundled for milestone M0/M1 testing, before the IRDB compiler exists
 * (ARCHITECTURE.md §3.4). They're Java resources, so they ship inside the APK unchanged.
 */
object StarterLibrary {

    data class Entry(val id: String, val label: String, val brand: String, val category: DeviceCategory, val resource: String)

    val entries: List<Entry> = listOf(
        Entry("starter-samsung-tv", "Samsung TV", "Samsung", DeviceCategory.TV, "samsung_tv.ir"),
        Entry("starter-lg-tv", "LG TV", "LG", DeviceCategory.TV, "lg_tv.ir"),
        Entry("starter-sony-tv", "Sony TV", "Sony", DeviceCategory.TV, "sony_tv.ir"),
    )

    fun load(entry: Entry): IrFile {
        val stream = StarterLibrary::class.java.getResourceAsStream("/fancyir-starter/${entry.resource}")
            ?: error("missing starter resource ${entry.resource}")
        return FlipperIrParser.parse(stream.bufferedReader().use { it.readText() })
    }
}
