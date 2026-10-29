package io.github.cocokoniq.fancyir.core.sensors

import android.content.Context
import android.os.Build
import android.provider.Settings
import io.github.cocokoniq.fancyir.core.domain.hardware.DeviceIdentity
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterMatch
import io.github.cocokoniq.fancyir.core.domain.hardware.EmitterMatcher

/** This phone's identity for the emitter registry (docs/hardware/rear-camera-ir-blaster-spec.md §5.D). */
object PhoneHardware {

    fun identity(context: Context): DeviceIdentity = DeviceIdentity(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        brand = Build.BRAND.orEmpty(),
        model = Build.MODEL.orEmpty(),
        // Defaults to the marketing name ("Xiaomi 15 Pro"); Build.MODEL is often a code.
        deviceName = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull(),
        device = Build.DEVICE.orEmpty(),
    )

    fun emitterMatch(context: Context): EmitterMatch? = EmitterMatcher.match(identity(context))
}
