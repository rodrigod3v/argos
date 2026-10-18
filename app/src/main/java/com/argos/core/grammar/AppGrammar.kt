package com.argos.core.grammar

import com.argos.core.model.AppEntry
import com.argos.core.text.PronunciationVariants
import com.argos.core.text.norm

/**
 * Gramatica restrita para o Vosk, montada a partir dos apps instalados.
 *
 * Por que existe: o modelo pt-BR tem vocabulario fechado. "chrome" vira "mim",
 * "prumo" vira "para um". Dentro de uma gramatica essas palavras viram
 * vocabulario de verdade e sao reconhecidas.
 *
 * Custo: comandos fora da gramatica sao destruidos. Por isso isso NAO e a
 * escuta principal — serve so pra re-decodificar quando o app nao foi achado.
 *
 * Puro: sem Android, testavel em JVM.
 */
object AppGrammar {

    private val PREFIXES = listOf("abrir", "abre", "abrir o")

    fun build(apps: List<AppEntry>): String {
        val phrases = ArrayList<String>(apps.size * 8 + 1)
        phrases += UNKNOWN_TOKEN
        val seen = HashSet<String>()
        for (app in apps) {
            val names = PronunciationVariants.of(app.label)
            for (name in names) {
                for (prefix in PREFIXES) {
                    val phrase = "$prefix $name"
                    if (seen.add(phrase)) phrases += phrase
                }
            }
        }
        return phrases.joinToString(prefix = "[", postfix = "]", separator = ",") { quote(it) }
    }

    private const val UNKNOWN_TOKEN = "[unk]"

    /** escape JSON minimo — labels vem do sistema, podem ter aspas. */
    private fun quote(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (character in value) {
            when (character) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (character < ' ') out.append("\\u%04x".format(character.code))
                else out.append(character)
            }
        }
        out.append('"')
        return out.toString()
    }
}
