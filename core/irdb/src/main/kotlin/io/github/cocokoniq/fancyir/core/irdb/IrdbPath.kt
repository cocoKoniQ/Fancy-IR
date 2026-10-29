package io.github.cocokoniq.fancyir.core.irdb

data class IrdbEntry(val category: DeviceCategory, val brand: String, val model: String)

/** Reads category, brand and model from a Flipper-IRDB path like `TVs/Samsung/Samsung_UE40.ir`. */
object IrdbPath {

    fun parse(path: String): IrdbEntry? {
        val parts = path.replace('\\', '/').trim('/').split('/')
        if (parts.size < 3 || !parts.last().endsWith(".ir", ignoreCase = true)) return null
        val category = DeviceCategory.fromIrdbFolder(parts[0])
        val brand = parts[1].replace('_', ' ').trim()
        val fileName = parts.last().dropLast(3)
        val model = fileName
            .removePrefix(parts[1]).removePrefix(brand)
            .trimStart('_', '-', ' ')
            .ifEmpty { fileName }
        return IrdbEntry(category, brand, model)
    }
}
