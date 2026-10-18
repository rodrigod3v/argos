package com.argos.di

import android.content.Context
import com.argos.core.routing.CommandRouter
import com.argos.data.app.PackageAppRepository
import com.argos.data.lexicon.AssetVerbLexicon
import com.argos.data.screen.AccessibilityScreenController
import com.argos.data.screen.NullScreenController
import com.argos.data.screen.NodeSource
import com.argos.data.system.AndroidStatusSink
import com.argos.data.voice.AndroidSpeechInput
import com.argos.data.voice.AndroidSpeechOutput
import com.argos.data.voice.VoskModelInstaller
import com.argos.data.voice.VoskSpeechInput
import com.argos.domain.port.AppRepository
import com.argos.domain.port.ScreenController
import com.argos.domain.port.SpeechInput
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
    val speechOutput: SpeechOutput by lazy { AndroidSpeechOutput(appContext) }
    val status: AndroidStatusSink by lazy { AndroidStatusSink(appContext) }

    /** Tier 0. Lexico de verbos vem do corpus pt-BR empacotado no APK. */
    val router: CommandRouter by lazy { CommandRouter(AssetVerbLexicon.load(appContext)) }

    /**
     * Ouvidos: Vosk offline por padrao. Se por algum motivo o modelo nao
     * estiver empacotado, cai no servico do sistema em vez de ficar mudo.
     */
    val speechInput: SpeechInput by lazy {
        if (VoskModelInstaller.isAvailable(appContext)) {
            VoskSpeechInput(VoskModelInstaller(appContext))
        } else {
            AndroidSpeechInput(appContext)
        }
    }

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
