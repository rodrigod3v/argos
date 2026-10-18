package com.argos.entry

import android.app.Application
import com.argos.di.Graph

/** Unico ponto que inicializa o container. */
class ArgosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
