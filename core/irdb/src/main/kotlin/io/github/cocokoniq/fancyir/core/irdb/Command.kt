package io.github.cocokoniq.fancyir.core.irdb

/** Semantic button vocabulary shared by every device; database names are normalised into it. */
enum class Command {
    POWER, POWER_ON, POWER_OFF,
    VOLUME_UP, VOLUME_DOWN, MUTE,
    CHANNEL_UP, CHANNEL_DOWN,
    INPUT,
    UP, DOWN, LEFT, RIGHT, OK, BACK, HOME, MENU, INFO, EXIT, GUIDE,
    PLAY, PAUSE, PLAY_PAUSE, STOP, NEXT, PREVIOUS, FAST_FORWARD, REWIND,
    SUBTITLES, SLEEP,
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
    TEMP_UP, TEMP_DOWN, MODE, FAN_SPEED, SWING, TURBO, TIMER,
    SPEED_UP, SPEED_DOWN, OSCILLATE, LIGHT,
    BRIGHTNESS_UP, BRIGHTNESS_DOWN,
    SOUND_MODE, BASS_UP, BASS_DOWN;

    /** Commands a probe must never send without asking (ARCHITECTURE.md §2.4). */
    val isDisruptive: Boolean get() = this == POWER || this == POWER_OFF
}
