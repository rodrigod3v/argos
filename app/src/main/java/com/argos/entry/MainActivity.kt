package com.argos.entry

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.argos.BuildConfig
import com.argos.di.Graph

/**
 * Onboarding + console. UI programatica de proposito:
 * zero AppCompat, zero layout XML, zero dependencia.
 */
class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var logView: TextView
    private lateinit var serviceButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildConsole())
        requestRuntimePermissions()
        if (BuildConfig.DEBUG) handleDebugIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        Graph.status.observer = { message -> runOnUiThread { appendLog(message) } }
        refresh()
    }

    /** singleTop: um relancamento entrega o intent aqui em vez de abrir outra instancia. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (BuildConfig.DEBUG) handleDebugIntent(intent)
    }

    override fun onPause() {
        Graph.status.observer = null
        super.onPause()
    }

    // ---- ui ----------------------------------------------------------------

    private fun buildConsole(): ViewGroup {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }

        root.addView(TextView(this).apply {
            text = "Argos"
            textSize = 26f
            setTextColor(Color.BLACK)
        })
        root.addView(TextView(this).apply {
            text = "assistente de voz on-device"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, dp(18))
        })

        root.addView(button("1. Ligar Acessibilidade (maos)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, fullWidth())

        root.addView(button("2. Conceder microfone") { requestRuntimePermissions() }, fullWidth())

        serviceButton = button("") { toggleService() }
        root.addView(serviceButton, fullWidth())

        root.addView(TextView(this).apply {
            text = "Diga: \"Argos abrir o aplicativo Prumo\""
            textSize = 13f
            setPadding(0, dp(14), 0, dp(6))
        })

        statusView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(0, 90, 0))
        }
        root.addView(statusView)

        logView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.DKGRAY)
            typeface = Typeface.MONOSPACE
        }
        root.addView(ScrollView(this).apply {
            addView(logView)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            )
            setPadding(0, dp(12), 0, 0)
        })

        return root
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(8) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---- permissoes --------------------------------------------------------

    private fun requestRuntimePermissions() {
        val needed = buildList {
            if (!granted(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !granted(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), REQUEST_CODE)
    }

    private fun granted(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // ---- servico -----------------------------------------------------------

    private fun toggleService() {
        if (ArgosVoiceService.running) {
            stopService(Intent(this, ArgosVoiceService::class.java))
        } else {
            startForegroundService(
                Intent(this, ArgosVoiceService::class.java).setAction(ArgosVoiceService.ACTION_START),
            )
        }
        refresh()
    }

    private fun refresh() {
        serviceButton.text = if (ArgosVoiceService.running) "Parar Argos" else "Iniciar Argos"
        statusView.text = buildString {
            append("acessibilidade: ")
                .append(if (ArgosAccessibilityService.connected) "ON" else "OFF")
                .append('\n')
            append("microfone: ")
                .append(if (granted(Manifest.permission.RECORD_AUDIO)) "OK" else "negado")
                .append('\n')
            append("STT offline: ")
                .append(if (Graph.speechInput.supportsOffline) "SIM" else "nao (usa servico do sistema)")
                .append('\n')
            append("servico: ")
                .append(if (ArgosVoiceService.running) "rodando" else "parado")
        }
    }

    private fun appendLog(message: String) {
        logView.text = "> $message\n${logView.text}"
    }

    // ---- gancho de teste (somente debug) -----------------------------------
    //
    //   adb shell am start -n com.argos/.entry.MainActivity --es cmd "abrir chrome"
    //   adb shell am start -n com.argos/.entry.MainActivity --ez voice true
    //   adb shell am start -n com.argos/.entry.MainActivity --ez selftest true
    // Compila fora do build de release (BuildConfig.DEBUG).

    private fun handleDebugIntent(intent: Intent?) {
        intent ?: return

        if (intent.getBooleanExtra(EXTRA_VOICE, false) && !ArgosVoiceService.running) {
            toggleService()
        }

        if (intent.getBooleanExtra(EXTRA_SELFTEST, false)) {
            val phrase = intent.getStringExtra(EXTRA_PHRASE)?.takeIf { it.isNotBlank() }
                ?: SELFTEST_PHRASE
            appendLog("[auto] selftest: TTS -> WAV -> Vosk (\"$phrase\")")
            DebugSelfTest(this).run(phrase) { result ->
                appendLog("[auto] selftest -> $result")
            }
        }

        val text = intent.getStringExtra(EXTRA_COMMAND)?.takeIf { it.isNotBlank() } ?: return
        val reply = Graph.handleCommand.invoke(Graph.router.route(text))
        Log.i(TAG, "debug cmd=\"$text\" -> $reply")
        appendLog("[auto] $text -> $reply")
    }

    private companion object {
        const val REQUEST_CODE = 42
        const val EXTRA_COMMAND = "cmd"
        const val EXTRA_VOICE = "voice"
        const val EXTRA_SELFTEST = "selftest"
        const val EXTRA_PHRASE = "phrase"
        const val SELFTEST_PHRASE = "argos abrir o prumo"
        const val TAG = "Argos"
    }
}
