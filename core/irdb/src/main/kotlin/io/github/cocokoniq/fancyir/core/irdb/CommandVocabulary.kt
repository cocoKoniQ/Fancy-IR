package io.github.cocokoniq.fancyir.core.irdb

/**
 * Maps the many spellings found in community databases ("Power", "PWR", "Vol_up", "VOL+",
 * "Ch_prev"…) onto [Command]. Returns null for names it doesn't know; callers keep those as
 * custom buttons so the long tail survives.
 */
object CommandVocabulary {

    private val aliases: Map<String, Command> = buildMap {
        fun alias(command: Command, vararg keys: String) = keys.forEach { put(it, command) }

        alias(Command.POWER, "power", "pwr", "power_toggle", "on_off", "onoff", "standby", "power_onoff")
        alias(Command.POWER_ON, "power_on", "on", "pwr_on", "poweron", "discrete_on")
        alias(Command.POWER_OFF, "power_off", "off", "pwr_off", "poweroff", "discrete_off")
        alias(Command.VOLUME_UP, "vol_up", "volume_up", "vol_plus", "volume_plus", "volup", "vol_inc", "v_plus")
        alias(Command.VOLUME_DOWN, "vol_dn", "vol_down", "volume_down", "vol_minus", "volume_minus", "voldown", "vol_dwn", "vol_dec", "v_minus")
        alias(Command.MUTE, "mute", "muting", "mute_toggle")
        alias(Command.CHANNEL_UP, "ch_up", "ch_next", "channel_up", "ch_plus", "chup", "channel_plus", "prog_plus", "p_plus", "chan_up")
        alias(Command.CHANNEL_DOWN, "ch_dn", "ch_down", "ch_prev", "channel_down", "ch_minus", "chdown", "channel_minus", "prog_minus", "p_minus", "chan_down")
        alias(Command.INPUT, "input", "source", "src", "input_select", "inputs", "av", "tv_av", "input_source")
        alias(Command.UP, "up", "cursor_up", "arrow_up", "nav_up", "dpad_up")
        alias(Command.DOWN, "down", "cursor_down", "arrow_down", "nav_down", "dpad_down", "dn")
        alias(Command.LEFT, "left", "cursor_left", "arrow_left", "nav_left", "dpad_left")
        alias(Command.RIGHT, "right", "cursor_right", "arrow_right", "nav_right", "dpad_right")
        alias(Command.OK, "ok", "enter", "select", "confirm", "center", "ok_enter")
        alias(Command.BACK, "back", "return", "rtn", "ret")
        alias(Command.HOME, "home", "smart_hub", "smarthub")
        alias(Command.MENU, "menu", "settings", "setup", "tools")
        alias(Command.INFO, "info", "display", "disp", "osd")
        alias(Command.EXIT, "exit")
        alias(Command.GUIDE, "guide", "epg")
        alias(Command.PLAY, "play")
        alias(Command.PAUSE, "pause")
        alias(Command.PLAY_PAUSE, "play_pause", "playpause")
        alias(Command.STOP, "stop")
        alias(Command.NEXT, "next", "skip_next", "skip_fwd", "next_track")
        alias(Command.PREVIOUS, "prev", "previous", "skip_prev", "skip_back", "prev_track")
        alias(Command.FAST_FORWARD, "ff", "fast_forward", "fwd", "forward")
        alias(Command.REWIND, "rew", "rewind", "rw", "fr")
        alias(Command.SUBTITLES, "subtitle", "subtitles", "sub", "subt", "cc", "caption")
        alias(Command.SLEEP, "sleep", "sleep_timer")
        alias(Command.TEMP_UP, "temp_up", "temp_plus", "temperature_up", "t_up")
        alias(Command.TEMP_DOWN, "temp_dn", "temp_down", "temp_minus", "temperature_down", "t_down")
        alias(Command.MODE, "mode")
        alias(Command.FAN_SPEED, "fan", "fan_speed", "speed", "wind_speed")
        alias(Command.SWING, "swing", "swing_toggle", "louver", "flap")
        alias(Command.TURBO, "turbo", "powerful", "jet")
        alias(Command.TIMER, "timer")
        alias(Command.SPEED_UP, "speed_up", "speed_plus", "fan_up", "fan_plus")
        alias(Command.SPEED_DOWN, "speed_down", "speed_dn", "speed_minus", "fan_down", "fan_minus")
        alias(Command.OSCILLATE, "osc", "oscillate", "oscillation", "rotate", "rotation")
        alias(Command.LIGHT, "light", "led", "lamp")
        alias(Command.BRIGHTNESS_UP, "bright_up", "brightness_up", "brighter", "bright_plus", "brightness_plus")
        alias(Command.BRIGHTNESS_DOWN, "bright_down", "brightness_down", "dimmer", "bright_minus", "brightness_minus")
        alias(Command.SOUND_MODE, "sound_mode", "sound", "surround", "effect")
        alias(Command.BASS_UP, "bass_up", "bass_plus")
        alias(Command.BASS_DOWN, "bass_down", "bass_dn", "bass_minus")
        for (digit in 0..9) {
            val command = Command.valueOf("DIGIT_$digit")
            alias(command, "$digit", "num_$digit", "key_$digit", "digit_$digit", "btn_$digit")
        }
    }

    private val upWords = setOf("up", "plus", "inc", "increase")
    private val downWords = setOf("down", "dn", "minus", "dec", "decrease")

    fun normalize(buttonName: String): Command? {
        val key = canonicalKey(buttonName)
        aliases[key]?.let { return it }

        val tokens = key.split('_').toSet()
        val up = tokens.any { it in upWords }
        val down = tokens.any { it in downWords }
        if (up == down) return null
        return when {
            "vol" in tokens || "volume" in tokens -> if (up) Command.VOLUME_UP else Command.VOLUME_DOWN
            "ch" in tokens || "channel" in tokens || "prog" in tokens -> if (up) Command.CHANNEL_UP else Command.CHANNEL_DOWN
            "temp" in tokens || "temperature" in tokens -> if (up) Command.TEMP_UP else Command.TEMP_DOWN
            "speed" in tokens || "fan" in tokens -> if (up) Command.SPEED_UP else Command.SPEED_DOWN
            "bright" in tokens || "brightness" in tokens -> if (up) Command.BRIGHTNESS_UP else Command.BRIGHTNESS_DOWN
            else -> null
        }
    }

    /** "VOL+" → "vol_plus", "Ch-" → "ch_minus", "Vol-Up" → "vol_up", "Play/Pause" → "play_pause". */
    internal fun canonicalKey(name: String): String =
        name.trim().lowercase()
            .replace("+", " plus ")
            .replace(Regex("-(?![a-z])"), " minus ")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
}
