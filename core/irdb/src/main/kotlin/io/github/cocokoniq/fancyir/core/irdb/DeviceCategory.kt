package io.github.cocokoniq.fancyir.core.irdb

/** Our device taxonomy, mapped from Flipper-IRDB's top-level folders. */
enum class DeviceCategory(val label: String, val irdbFolders: Set<String>) {
    TV("TV", setOf("TVs")),
    MONITOR("Monitor", setOf("Monitors", "Touchscreen_Displays")),
    PROJECTOR("Projector", setOf("Projectors")),
    SOUNDBAR("Soundbar", setOf("SoundBars")),
    AV_RECEIVER("AV receiver", setOf("Audio_and_Video_Receivers")),
    SPEAKER("Speaker", setOf("Speakers")),
    AIR_CONDITIONER("Air conditioner", setOf("ACs")),
    FAN("Fan", setOf("Fans")),
    LIGHT("Light", setOf("LED_Lighting")),
    SET_TOP_BOX("Set-top box", setOf("Cable_Boxes", "DVB-T", "TV_Tuner")),
    STREAMING_DEVICE("Streaming device", setOf("Streaming_Devices")),
    MEDIA_PLAYER("Media player", setOf("Blu-Ray", "DVD_Players", "CD_Players", "VCR", "Multimedia")),
    AIR_PURIFIER("Air purifier", setOf("Air_Purifiers")),
    HEATER("Heater", setOf("Heaters", "Fireplaces")),
    HUMIDIFIER("Humidifier", setOf("Humidifiers")),
    OTHER("Device", emptySet());

    companion object {
        private val byFolder = entries.flatMap { c -> c.irdbFolders.map { it.lowercase() to c } }.toMap()

        fun fromIrdbFolder(folder: String): DeviceCategory = byFolder[folder.trim().lowercase()] ?: OTHER
    }
}
