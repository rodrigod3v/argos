package com.argos.di

import android.content.Context
import com.argos.data.app.PackageAppRepository
import com.argos.data.screen.AccessibilityScreenController
import com.argos.data.screen.NullScreenController
import com.argos.data.screen.NodeSource
import com.argos.data.system.AndroidStatusSink
import com.argos.data.voice.AndroidSpeechInput
import com.argos.data.voice.AndroidSpeechOutput
import com.argos.domain.port.AppRepository
import com.argos.domain.port.ScreenController
import com.argos.domain.port.SpeechOutput
import com.argos.domain.usecase.HandleCommand

/**
 * Container manual. Zero anotacao, zero codegen, zero reflection.
 * Inicializado uma vez no ArgosApp (Application).
 */
object Graph {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val apps: AppRepository by lazy { PackageAppRepository(appContext) }
    val speechInput: AndroidSpeechInput by lazy { AndroidSpeechInput(appContext) }
    val speechOutput: SpeechOutput by lazy { AndroidSpeechOutput(appContext) }
    val status: AndroidStatusSink by lazy { AndroidStatusSink(appContext) }

    /** Troca conforme o servico de acessibilidade liga/desliga. */
    @Volatile
    var screen: ScreenController = NullScreenController
        private set

    fun installScreen(source: NodeSource) {
        screen = AccessibilityScreenController(source)
    }

    fun clearScreen() {
        screen = NullScreenController
    }

    val handleCommand: HandleCommand by lazy { HandleCommand(apps) { screen } }
}
