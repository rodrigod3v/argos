package com.argos.data.lexicon

import android.content.Context
import android.util.Log
import com.argos.core.routing.VerbLexicon

/**
 * Carrega o lexico de verbos do APK (assets/verbos.txt).
 * Se o asset faltar, cai no piso embutido — o app nunca fica sem roteador.
 */
object AssetVerbLexicon {

    private const val TAG = "Argos"
    private const val ASSET = "verbos.txt"

    fun load(context: Context): VerbLexicon = runCatching {
        context.assets.open(ASSET).bufferedReader().useLines { lines ->
            VerbLexicon.from(lines)
        }
    }.onSuccess {
        Log.i(TAG, "lexico carregado: ${it.size} formas verbais")
    }.getOrElse {
        Log.w(TAG, "lexico de assets indisponivel, usando piso embutido", it)
        VerbLexicon.EMPTY
    }
}
