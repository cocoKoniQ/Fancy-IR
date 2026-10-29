package io.github.cocokoniq.fancyir

import android.app.Application

class FancyIrApplication : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
