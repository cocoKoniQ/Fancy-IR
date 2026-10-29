package io.github.cocokoniq.fancyir.core.irdb.smartir

import io.github.cocokoniq.fancyir.core.ir.BroadlinkCodec
import io.github.cocokoniq.fancyir.core.ir.IrCode
import io.github.cocokoniq.fancyir.core.ir.ProntoCodec
import io.github.cocokoniq.fancyir.core.irdb.CommandVocabulary
import io.github.cocokoniq.fancyir.core.irdb.DeviceCategory
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCodeset
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogCommand
import io.github.cocokoniq.fancyir.core.irdb.catalog.CatalogSource
import io.github.cocokoniq.fancyir.core.irdb.catalog.ClimateSpec
import io.github.cocokoniq.fancyir.core.irdb.catalog.CodesetKind
import io.github.cocokoniq.fancyir.core.irdb.catalog.FunctionKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** SmartIR's device families: each has its own docs page, code folder and JSON shape. */
enum class SmartIrPlatform(val folder: String, val docsPage: String) {
    CLIMATE("climate", "CLIMATE.md"),
    MEDIA_PLAYER("media_player", "MEDIA_PLAYER.md"),
    FAN("fan", "FAN.md"),
    LIGHT("light", "LIGHT.md");

    companion object {
        fun forCategory(category: DeviceCategory): SmartIrPlatform? = when (category) {
            DeviceCategory.AIR_CONDITIONER, DeviceCategory.HEATER -> CLIMATE
            DeviceCategory.FAN, DeviceCategory.AIR_PURIFIER -> FAN
            DeviceCategory.LIGHT -> LIGHT
            DeviceCategory.TV, DeviceCategory.MONITOR, DeviceCategory.PROJECTOR, DeviceCategory.SOUNDBAR,
            DeviceCategory.AV_RECEIVER, DeviceCategory.SPEAKER, DeviceCategory.SET_TOP_BOX,
            DeviceCategory.STREAMING_DEVICE, DeviceCategory.MEDIA_PLAYER -> MEDIA_PLAYER
            DeviceCategory.HUMIDIFIER, DeviceCategory.OTHER -> null
        }
    }
}

/** One row of a SmartIR "Available codes" table. */
data class SmartIrIndexEntry(val manufacturer: String, val code: String, val models: List<String>, val controller: String)

/**
 * Parser for SmartIR (github.com/smartHomeHub/SmartIR, MIT): Home Assistant code files whose
 * climate tables hold one code per (mode, fan[, swing], temperature) state — exactly what a
 * stateful AC needs, without porting any LGPL protocol encoder.
 */
object SmartIr {

    const val LICENSE = "MIT (SmartIR, (c) 2019 Vassilis Panos)"

    private val json = Json { ignoreUnknownKeys = true }

    /** Rows of a docs page's "Available codes" tables, grouped under `#### Manufacturer` headings. */
    fun parseIndex(markdown: String): List<SmartIrIndexEntry> {
        val rows = ArrayList<SmartIrIndexEntry>()
        var manufacturer: String? = null
        val codeCell = Regex("""^\[(\d+)]\([^)]*\)$""")
        for (raw in markdown.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#### ")) {
                manufacturer = line.removePrefix("#### ").trim()
                continue
            }
            // Tables vary between pages: outer pipes are optional and some add a Notes column, so
            // read the code from the first cell, models from the second and the controller from the last.
            val cells = line.removePrefix("|").removeSuffix("|").split('|').map(String::trim)
            if (cells.size < 3) continue
            val code = codeCell.find(cells.first())?.groupValues?.get(1) ?: continue
            val maker = manufacturer ?: continue
            val models = cells[1].split(Regex("<br\\s*/?>"))
                .map { it.replace(Regex("<[^>]+>"), "").trim() }
                .filter { it.isNotEmpty() }
            rows += SmartIrIndexEntry(maker, code, models, cells.last())
        }
        return rows
    }

    /**
     * Parses one code file. Returns null for files we can't send (non-Broadlink raw encodings,
     * RF-only packets, or no decodable commands).
     */
    fun parse(text: String, platform: SmartIrPlatform, code: String, category: DeviceCategory, rank: Int): CatalogCodeset? {
        val root = json.parseToJsonElement(text).jsonObject
        val encoding = root.string("commandsEncoding") ?: return null
        val controller = root.string("supportedController").orEmpty()
        val decode: (String) -> IrCode.Raw = when {
            encoding.equals("Base64", ignoreCase = true) && controller.equals("Broadlink", ignoreCase = true) ->
                BroadlinkCodec::decodeBase64
            encoding.equals("Pronto", ignoreCase = true) -> ProntoCodec::decode
            else -> return null
        }
        val commandsJson = root["commands"] as? JsonObject ?: return null

        val commands = ArrayList<CatalogCommand>()
        flatten(commandsJson, emptyList()) { path, value ->
            val function = when (platform) {
                SmartIrPlatform.CLIMATE ->
                    if (path == listOf("off")) FunctionKey.CLIMATE_OFF else FunctionKey.climate(path)
                else -> path.singleOrNull()?.let(CommandVocabulary::normalize)?.let(FunctionKey::of)
                    ?: FunctionKey.custom(path.joinToString(":"))
            }
            runCatching { decode(value) }.getOrNull()?.let { commands += CatalogCommand(function, it) }
        }
        if (commands.isEmpty()) return null

        val manufacturer = root.string("manufacturer") ?: "Unknown"
        val models = (root["supportedModels"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val climate = if (platform == SmartIrPlatform.CLIMATE) {
            ClimateSpec(
                minTempC = root["minTemperature"]?.jsonPrimitive?.intOrNull ?: 16,
                maxTempC = root["maxTemperature"]?.jsonPrimitive?.intOrNull ?: 30,
                precision = root["precision"]?.jsonPrimitive?.floatOrNull ?: 1f,
                modes = root.strings("operationModes"),
                fanModes = root.strings("fanModes"),
                swingModes = root.strings("swingModes"),
            )
        } else {
            null
        }
        return CatalogCodeset(
            id = "${CatalogSource.SMARTIR.tag}:${platform.folder}:$code",
            kind = if (climate != null) CodesetKind.CLIMATE else CodesetKind.COMMANDS,
            category = category,
            brand = manufacturer,
            label = models.take(3).joinToString(" / ").ifEmpty { "SmartIR $code" },
            rank = rank,
            source = CatalogSource.SMARTIR,
            sourceRef = "codes/${platform.folder}/$code.json",
            license = LICENSE,
            commands = commands.distinctBy { it.function },
            climate = climate,
        )
    }

    private fun flatten(element: JsonElement, path: List<String>, visit: (List<String>, String) -> Unit) {
        when (element) {
            is JsonObject -> element.forEach { (key, child) -> flatten(child, path + key, visit) }
            is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }?.let { visit(path, it) }
            is JsonArray -> Unit
        }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
}
